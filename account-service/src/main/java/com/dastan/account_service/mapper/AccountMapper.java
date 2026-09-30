package com.dastan.account_service.mapper;

import com.dastan.account_service.dto.AccountRequest;
import com.dastan.account_service.dto.AccountResponse;
import com.dastan.account_service.entity.Account;

public class AccountMapper {
    public static Account toEntity(AccountRequest request) {
        if (request == null) return null;

        return Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .accountType(request.getAccountType())
                .balance(request.getInitialDeposit())
                .build();
    }

    public static AccountResponse toResponse(Account account) {
        if (account == null) return null;

        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .accountType(account.getAccountType())
                .accountStatus(account.getAccountStatus())
                .balance(account.getBalance())
                .dailyTransactionLimit(account.getDailyTransactionLimit())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
