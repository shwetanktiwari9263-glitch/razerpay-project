package com.recoveryagent.dto;

import com.recoveryagent.entity.ExecutionStatus;
import com.recoveryagent.entity.RecoveryChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryActionDto {

    private Long id;
    private Long aiAgentAnalysisId;
    private Long paymentEventId;
    private RecoveryChannel actionType;
    private ExecutionStatus executionStatus;
    private String newRazorpayLinkId;
    private String newPaymentId;
    private String actionMetadata;
    private String rulesApplied;
    private String executionError;
    private LocalDateTime triggeredAt;
    private LocalDateTime executedAt;
}
