package com.dastan.account_service.entity;

import java.math.BigDecimal;

public enum AccountType {
    FIXED_DEPOSIT(new BigDecimal("500000")),
    SAVINGS(new BigDecimal("100000")),
    CURRENT(new BigDecimal("500000"));


    private final BigDecimal dailyTransactionLimit;

    AccountType(BigDecimal dailyTransactionLimit) {
        this.dailyTransactionLimit = dailyTransactionLimit;
    }

    public BigDecimal getDailyTransactionLimit() {
        return dailyTransactionLimit;
    }
}
