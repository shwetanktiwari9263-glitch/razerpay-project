package com.recoveryagent.dto;

import com.recoveryagent.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Complete payment information combining payment event, ML prediction, and AI
 * analysis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDetailDto {

    // Payment Information
    private Long id;
    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String gateway;
    private String bankOrWallet;
    private String errorCode;
    private String errorDescription;
    private String customerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ML Prediction
    private BigDecimal failureProbability;
    private BigDecimal successProbability;
    private String riskLevel; // LOW, MEDIUM, HIGH
    private String mlModelVersion;
    private BigDecimal modelConfidence;

    // AI Analysis
    private String aiCauseAnalysis;
    private String aiExplanation;
    private String aiRecommendedAction;
    private String aiRecoveryStrategy;
    private String aiPriority; // LOW, MEDIUM, HIGH
    private BigDecimal aiConfidenceScore;

    // Recovery Status
    private String recoveryActionType;
    private String recoveryActionStatus;
    private Boolean recoverySuccess;
    private String recoveryError;
}
