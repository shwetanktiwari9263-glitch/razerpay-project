package com.recoveryagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

/**
 * Detailed failure analysis for dashboard
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailureAnalysisDto {

    // Error Code Distribution
    private Map<String, Long> errorCodeDistribution;
    private Map<String, BigDecimal> errorCodeFailureRate;

    // Payment Method Distribution
    private Map<String, Long> paymentMethodDistribution;
    private Map<String, BigDecimal> paymentMethodFailureRate;

    // Bank/Wallet Analysis
    private Map<String, Long> bankFailureDistribution;
    private Map<String, BigDecimal> bankFailureRate;

    // Root Cause Categories (from AI analysis)
    private Map<String, Long> rootCauseDistribution;

    // Risk Level Distribution
    private Map<String, Long> riskLevelDistribution;

    // Recent Failures
    private List<PaymentEventDto> recentFailures;

    // Recovery Success Rate by Error Code
    private Map<String, BigDecimal> recoverySuccessRateByErrorCode;
}
