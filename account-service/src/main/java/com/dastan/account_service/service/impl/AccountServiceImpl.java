package com.dastan.account_service.service.impl;

import com.dastan.account_service.dto.AccountRequest;
import com.dastan.account_service.dto.AccountResponse;
import com.dastan.account_service.entity.Account;
import com.dastan.account_service.entity.AccountStatus;
import com.dastan.account_service.mapper.AccountMapper;
import com.dastan.account_service.repository.AccountRepository;
import com.dastan.account_service.service.AccountService;
import com.dastan.account_service.util.AccountNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j

public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    public AccountResponse createAccount(AccountRequest request) {
        if(accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("");
        }

        Account account = AccountMapper.toEntity(request);
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setAccountNumber(accountNumberGenerator.generateAccountNumber());
        account.setDailyTransactionLimit(request.getAccountType().getDailyTransactionLimit());

        Account result = accountRepository.save(account);

        return AccountMapper.toResponse(result);
    }


    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                () -> new RuntimeException("")
        );

        return AccountMapper.toResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                () -> new RuntimeException("")
        );

        return account.getBalance();
    }

    public void blockAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                () -> new RuntimeException("")
        );

        account.setAccountStatus(AccountStatus.BLOCKED);

        accountRepository.save(account);

        log.info(accountNumber);
    }

    public void deductBalance(String accountNumber, BigDecimal amount) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                () -> new RuntimeException("")
        );

        if(account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("");
        }

        if(account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
    }

    public void depositBalance(String accountNumber, BigDecimal amount) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                () -> new RuntimeException("")
        );

        if(account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }
}
