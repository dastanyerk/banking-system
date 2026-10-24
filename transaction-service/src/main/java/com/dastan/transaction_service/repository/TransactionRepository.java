package com.dastan.transaction_service.repository;

import com.dastan.transaction_service.entity.Transaction;
import com.dastan.transaction_service.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findBySenderAccountNumberOrderByCreatedAtDesc(String accountNumber);
    List<Transaction> findBySenderAccountNumberAndTransactionStatusInAndCreatedAtAfter(
            String senderAccountNumber,
            Collection<TransactionStatus> statuses,
            LocalDateTime after);
}
