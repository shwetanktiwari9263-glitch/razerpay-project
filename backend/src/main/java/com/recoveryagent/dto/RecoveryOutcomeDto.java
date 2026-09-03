package com.recoveryagent.dto;

import com.recoveryagent.entity.OutcomeStatus;
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
public class RecoveryOutcomeDto {

    private Long id;
    private Long recoveryActionId;
    private Long originalPaymentEventId;
    private Long newPaymentEventId;
    private OutcomeStatus outcomeStatus;
    private Boolean recoverySuccess;
    private BigDecimal recoveredAmount;
    private Integer recoveryAttemptCount;
    private Integer timeToRecovery;
    private BigDecimal recoveryVelocityHours;
    private String feedbackNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
