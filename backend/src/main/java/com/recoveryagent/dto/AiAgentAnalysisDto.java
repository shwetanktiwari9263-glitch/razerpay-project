package com.recoveryagent.dto;

import com.recoveryagent.entity.RecoveryChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAgentAnalysisDto {

    private Long id;
    private BigDecimal confidenceScore;
    private String rootCauseAnalysis;
    private String generatedExplanation;
    private RecoveryChannel suggestedChannel;
    private BigDecimal suggestedAmount;
    private String retryStrategy;
    private String recoveryStrategy;
    private String aiModelName;
    private String reasoningChain;
    private LocalDateTime createdAt;
}
