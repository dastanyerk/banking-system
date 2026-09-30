package com.dastan.transaction_service.service;

import com.dastan.transaction_service.dto.TransactionRequest;
import com.dastan.transaction_service.dto.TransactionResponse;
import java.util.List;

public interface TransactionService {
    TransactionResponse transfer(TransactionRequest request);
    TransactionResponse getTransaction(String transactionId);
    List<TransactionResponse> getTransactionHistory(String accountNumber);
    TransactionResponse verifyOTP(String transactionId, String otp);
}
