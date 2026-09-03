package com.recoveryagent.dto;

import com.recoveryagent.entity.RecoveryChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI Analysis response for a payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysisResponseDto {

    private Long paymentId;
    private String paymentIdentifier;

    // Root Cause Analysis
    private String cause;
    private String explanation;
    private String technicalDetails;

    // Recovery Recommendation
    private RecoveryChannel recommendedAction;
    private String recoveryStrategy;
    private List<String> recommendedActions;
    private List<String> recoverySteps;
    private List<String> alternatives;

    // Additional Info
    private String priority; // LOW, MEDIUM, HIGH
    private BigDecimal confidenceScore;
    private String aiModel;
    private String reasoning; // Explanation of reasoning

    private Long analysisTimestamp;
}
