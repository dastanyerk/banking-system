package com.dastan.transaction_service.mapper;

import com.dastan.transaction_service.dto.TransactionRequest;
import com.dastan.transaction_service.dto.TransactionResponse;
import com.dastan.transaction_service.entity.Transaction;

public class TransactionMapper {

    public static Transaction toEntity(TransactionRequest request) {
        if (request == null) return null;

        return Transaction.builder()
                .senderAccountNumber(request.getSenderAccountNumber())
                .receiverAccountNumber(request.getReceiverAccountNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .build();
    }

    public static TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) return null;

        return TransactionResponse.builder()
                .id(transaction.getId())
                .senderAccountNumber(transaction.getSenderAccountNumber())
                .receiverAccountNumber(transaction.getReceiverAccountNumber())
                .amount(transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .transactionStatus(transaction.getTransactionStatus())
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .referenceNumber(transaction.getReferenceNumber())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
