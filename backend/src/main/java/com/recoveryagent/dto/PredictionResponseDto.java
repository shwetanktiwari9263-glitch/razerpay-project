package com.recoveryagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ML Prediction response for a payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionResponseDto {

    private Long paymentId;
    private String paymentIdentifier;
    private BigDecimal successProbability;
    private BigDecimal failureProbability;
    private String riskLevel; // LOW, MEDIUM, HIGH
    private BigDecimal modelConfidence;
    private String modelVersion;
    private String modelName;
    private String explanation; // Human-readable explanation
    private Long predictionTimestamp;
}
