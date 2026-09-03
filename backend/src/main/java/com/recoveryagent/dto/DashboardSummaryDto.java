package com.recoveryagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dashboard summary metrics and statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryDto {

    // Overall Metrics
    private Long totalPayments;
    private Long successfulPayments;
    private Long failedPayments;
    private BigDecimal successRate;
    private BigDecimal failureRate;

    // Recovery Metrics
    private Long highRiskPayments;
    private Long recoveredPayments;
    private BigDecimal totalRecoveredAmount;
    private BigDecimal averageRecoveryTime; // in minutes
    private BigDecimal recoveryRatePercent;

    // Risk Distribution
    private Long lowRiskCount;
    private Long mediumRiskCount;
    private Long highRiskCount;

    // Payment Method Metrics
    private String topFailingMethod;
    private BigDecimal topMethodFailureRate;

    // Time Period
    private String period; // TODAY, WEEK, MONTH, ALL_TIME
    private String lastUpdated;
}
