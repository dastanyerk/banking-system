package com.dastan.transaction_service.service.impl;

import com.dastan.transaction_service.client.AccountServiceClient;
import com.dastan.transaction_service.dto.TransactionRequest;
import com.dastan.transaction_service.dto.TransactionResponse;
import com.dastan.transaction_service.entity.Transaction;
import com.dastan.transaction_service.entity.TransactionStatus;
import com.dastan.transaction_service.entity.TransactionType;
import com.dastan.transaction_service.event.TransactionCompletedEvent;
import com.dastan.transaction_service.event.TransactionInitiatedEvent;
import com.dastan.transaction_service.mapper.TransactionMapper;
import com.dastan.transaction_service.repository.TransactionRepository;
import com.dastan.transaction_service.service.TransactionService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    public static final String OTP_KEY_PREFIX = "verification:otp:";
    private static final Duration FRAUD_WINDOW = Duration.ofSeconds(60);

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    @Override
    public TransactionResponse transfer(TransactionRequest request) {
        log.info("SAGA Start - Transfer: {} -> {} amount:  {}", request.getSenderAccountNumber(), request.getReceiverAccountNumber(), request.getAmount());

        accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());

        Transaction transaction = TransactionMapper.toEntity(request);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.PROCESSING);
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", saved.getId());

        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                saved.getId(), saved.getSenderAccountNumber(), saved.getReceiverAccountNumber(),
                saved.getAmount(), saved.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, saved.getId(), event);
        log.info("SAGA step 2: TransactionInitiatedEvent published: {}", saved.getId());

        return TransactionMapper.toResponse(saved);
    }

    @Override
    public TransactionResponse getTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(
                () -> new RuntimeException("Transaction not found: " + transactionId)
        );

        return TransactionMapper.toResponse(transaction);
    }

    @Override
    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream().map(TransactionMapper::toResponse).toList();
    }

    @Override
    public TransactionResponse verifyOTP(String transactionId, String otp) {
        log.info("OTP verification for the transaction: {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(
                () -> new RuntimeException("Transaction not found: " + transactionId)
        );

        if (transaction.getTransactionStatus() != TransactionStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Transaction is not awaiting verification: " + transactionId);
        }

        String otpKey = OTP_KEY_PREFIX + transactionId;
        String storedOtp = redisTemplate.opsForValue().getAndDelete(otpKey);

        if (storedOtp == null) {
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP is expired. Transaction cancelled and money refunded");
            return TransactionMapper.toResponse(transaction);
        }

        if (!storedOtp.equals(otp)) {
            log.warn("Wrong OTP. Blocking account and refunding money: {}", transactionId);
            blockAccountAndCompensate(transaction, "Wrong OTP entered. Transaction cancelled and account blocked for security");
            return TransactionMapper.toResponse(transaction);
        }

        log.info("OTP verified. Completing transaction: {}", transactionId);
        completeTransaction(transaction);
        return TransactionMapper.toResponse(transaction);
    }

    private void reverseRelatedTransactions(Transaction flagged, String reason) {
        LocalDateTime from = flagged.getCreatedAt().minus(FRAUD_WINDOW);

        List<Transaction> related = transactionRepository
                .findBySenderAccountNumberAndTransactionStatusInAndCreatedAtAfter(
                        flagged.getSenderAccountNumber(),
                        List.of(TransactionStatus.COMPLETED,
                                TransactionStatus.PROCESSING,
                                TransactionStatus.PENDING_VERIFICATION),
                        from);

        log.warn("Reversing {} related transactions for account {}",
                related.size(), flagged.getSenderAccountNumber());

        for (Transaction tx : related) {
            if (tx.getId().equals(flagged.getId())) {
                continue;
            }

            if (tx.getTransactionStatus() == TransactionStatus.COMPLETED) {
                reverseCompleted(tx, reason);
            } else {
                // ещё не дошло до получателя — вернуть отправителю
                redisTemplate.delete(OTP_KEY_PREFIX + tx.getId());
                compensateTransaction(tx, reason);
            }
        }
    }

    private void reverseCompleted(Transaction transaction, String reason) {
        try {
            accountServiceClient.deductBalance(transaction.getReceiverAccountNumber(), transaction.getAmount());
            accountServiceClient.depositBalance(transaction.getSenderAccountNumber(), transaction.getAmount());

            transaction.setTransactionStatus(TransactionStatus.REVERSED);
            transaction.setFailureReason("Reversed as part of fraudulent series: " + reason);
            transactionRepository.save(transaction);

            Map<String, Object> refundEvent = new HashMap<>();
            refundEvent.put("transactionId", transaction.getId());
            refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
            refundEvent.put("amount", transaction.getAmount());
            refundEvent.put("reason", reason);
            kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

            log.warn("Transaction {} reversed: {} returned to {}",
                    transaction.getId(), transaction.getAmount(), transaction.getSenderAccountNumber());

        } catch (FeignException e) {
            transaction.setTransactionStatus(TransactionStatus.REVERSAL_FAILED);
            transaction.setFailureReason("Reversal failed: " + e.getMessage());
            transactionRepository.save(transaction);
            log.error("Could not reverse transaction {}", transaction.getId(), e);
        }
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION. Refunding: {}, Amount: {}", transaction.getSenderAccountNumber(), transaction.getAmount());

        // Money back to sender
        accountServiceClient.depositBalance(transaction.getSenderAccountNumber(), transaction.getAmount());

        transaction.setTransactionStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason + "SAGA COMPENSATION executed, money refunded at: " + LocalDateTime.now());
        transactionRepository.save(transaction);

        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);
        log.info("SAGA COMPENSATION complete - {} refunded to {}", transaction.getAmount(), transaction.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);
        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("fraud.detected published - account {} will be blocked", transaction.getSenderAccountNumber());

        compensateTransaction(transaction, reason);

        try {
            reverseRelatedTransactions(transaction, reason);
        } catch (Exception e) {
            log.error("Failed to reverse related transactions for {}", transaction.getSenderAccountNumber(), e);
        }
    }

    private void completeTransaction(Transaction transaction) {
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(), transaction.getSenderAccountNumber(), transaction.getReceiverAccountNumber(),
                transaction.getAmount(), transaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);

        log.info("SAGA COMPLETE - Transaction {} completed", transaction.getId());
    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(
                () -> new RuntimeException("Transaction not found: " + transactionId)
        );

        if (transaction.getTransactionStatus() != TransactionStatus.PROCESSING) {
            log.warn("Transaction {} not PROCESSING - skipping", transactionId);
            return;
        }

        completeTransaction(transaction);
    }
}
