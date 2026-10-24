package com.dastan.transaction_service.entity;

public enum TransactionStatus {
    PENDING, PENDING_VERIFICATION, PROCESSING, COMPLETED, FLAGGED, FAILED, REVERSED, REVERSAL_FAILED
}
