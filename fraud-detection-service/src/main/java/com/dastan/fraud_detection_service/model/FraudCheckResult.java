package com.dastan.fraud_detection_service.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class FraudCheckResult {
    private boolean fraud;
    private String reason;
}
