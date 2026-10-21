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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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

        String otpKey = "verification:otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null) {
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP is expired. Transaction cancelled and money refunded");
            return TransactionMapper.toResponse(transaction);
        }

        if (!storedOtp.equals(otpKey)) {
            log.warn("Wrong OTP. Blocking account and refunding money: {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction, "Wrong OTP entered. Transaction cancelled and account blocked for security");

            return TransactionMapper.toResponse(transaction);
        }

        log.info("OTP verified. Completing transaction: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);

        return TransactionMapper.toResponse(transaction);
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
        log.warn("fraud.detected published - account: {} will be blocked. Kindly contact to the bank", transaction.getSenderAccountNumber());

        compensateTransaction(transaction, reason);
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
