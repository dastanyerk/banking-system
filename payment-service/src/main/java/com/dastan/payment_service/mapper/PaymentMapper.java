package com.dastan.payment_service.mapper;

import com.dastan.payment_service.dto.PaymentRequest;
import com.dastan.payment_service.dto.PaymentResponse;
import com.dastan.payment_service.model.Payment;
import com.dastan.payment_service.model.PaymentStatus;

public class PaymentMapper {

    public static final String CURRENCY = "USD";

    public static Payment toEntity(PaymentRequest request, String razorpayOrderId) {
        return Payment.builder()
                .razorpayOrderId(razorpayOrderId)
                .accountNumber(request.getAccountNumber())
                .amount(request.getAmount())
                .currency(CURRENCY)
                .status(PaymentStatus.CREATED)
                .description(request.getDescription())
                .build();
    }

    public static PaymentResponse toResponse(Payment payment, String keyId) {
        return PaymentResponse.builder()
                .paymentId(String.valueOf(payment.getId()))
                .razorpayOrderId(payment.getRazorpayOrderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .razorpayKeyId(keyId)
                .build();
    }
}
