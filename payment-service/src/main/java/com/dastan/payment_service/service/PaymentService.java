package com.dastan.payment_service.service;

import com.dastan.payment_service.dto.PaymentRequest;
import com.dastan.payment_service.dto.PaymentResponse;
import com.razorpay.RazorpayException;

import java.util.Map;

public interface PaymentService {
    PaymentResponse createPaymentOrder(PaymentRequest request) throws RazorpayException;
    void handleWebhook(Map<String, Object> payload);
}
