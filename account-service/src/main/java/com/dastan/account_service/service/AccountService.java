package com.dastan.account_service.service;

import com.dastan.account_service.dto.AccountRequest;
import com.dastan.account_service.dto.AccountResponse;
import java.math.BigDecimal;

public interface AccountService {
    AccountResponse createAccount(AccountRequest request);
    AccountResponse getAccount(String accountNumber);
    BigDecimal getBalance(String accountNumber);
    void blockAccount(String accountNumber);
    void unblockAccount(String accountNumber);
    void deductBalance(String accountNumber, BigDecimal amount);
    void depositBalance(String accountNumber, BigDecimal amount);
}
