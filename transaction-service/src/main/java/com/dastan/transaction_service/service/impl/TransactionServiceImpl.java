package com.dastan.transaction_service.service.impl;

import com.dastan.transaction_service.client.AccountServiceClient;
import com.dastan.transaction_service.dto.TransactionRequest;
import com.dastan.transaction_service.dto.TransactionResponse;
import com.dastan.transaction_service.entity.Transaction;
import com.dastan.transaction_service.entity.TransactionStatus;
import com.dastan.transaction_service.entity.TransactionType;
import com.dastan.transaction_service.event.TransactionInitiatedEvent;
import com.dastan.transaction_service.mapper.TransactionMapper;
import com.dastan.transaction_service.repository.TransactionRepository;
import com.dastan.transaction_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

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
                () -> new RuntimeException("")
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
        return null;
    }

}
