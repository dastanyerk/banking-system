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

    @Override
    public AccountResponse createAccount(AccountRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Account with email " + request.getEmail() + " already exists");
        }

        Account account = AccountMapper.toEntity(request);
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setAccountNumber(accountNumberGenerator.generateAccountNumber());
        account.setDailyTransactionLimit(request.getAccountType().getDailyTransactionLimit());

        Account result = accountRepository.save(account);
        log.info("Account created: {}", result.getAccountNumber());

        return AccountMapper.toResponse(result);
    }

    @Override
    public AccountResponse getAccount(String accountNumber) {
        return AccountMapper.toResponse(findAccount(accountNumber));
    }

    @Override
    public BigDecimal getBalance(String accountNumber) {
        return findAccount(accountNumber).getBalance();
    }

    @Override
    public void blockAccount(String accountNumber) {
        Account account = findAccount(accountNumber);

        if (account.getAccountStatus() == AccountStatus.CLOSED) {
            throw new RuntimeException("Account " + accountNumber + " is closed and cannot be blocked");
        }

        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.warn("Account {} is BLOCKED", accountNumber);
    }

    @Override
    public void unblockAccount(String accountNumber) {
        Account account = findAccount(accountNumber);

        if (account.getAccountStatus() != AccountStatus.BLOCKED) {
            throw new RuntimeException("Account " + accountNumber + " is not blocked (status: "
                    + account.getAccountStatus() + ")");
        }

        account.setAccountStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
        log.info("Account {} is UNBLOCKED", accountNumber);
    }

    @Override
    public void deductBalance(String accountNumber, BigDecimal amount) {
        validateAmount(amount);
        Account account = findAccount(accountNumber);

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account " + accountNumber + " is "
                    + account.getAccountStatus() + ". Outgoing transactions are not allowed");
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds on account " + accountNumber
                    + ". Balance: " + account.getBalance() + ", requested: " + amount);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Deducted {} from account {}", amount, accountNumber);
    }

    @Override
    public void depositBalance(String accountNumber, BigDecimal amount) {
        validateAmount(amount);
        Account account = findAccount(accountNumber);

        // BLOCKED-счёт может получать деньги (например, возврат), но не отправлять
        if (account.getAccountStatus() == AccountStatus.CLOSED) {
            throw new RuntimeException("Account " + accountNumber + " is closed. Deposits are not allowed");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Deposited {} to account {}", amount, accountNumber);
    }

    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow(
                () -> new RuntimeException("Account not found: " + accountNumber)
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero, got: " + amount);
        }
    }
}