package com.recoveryagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_outcomes", indexes = {
    @Index(name = "idx_recovery_action_id", columnList = "recovery_action_id"),
    @Index(name = "idx_original_payment_id", columnList = "original_payment_event_id"),
    @Index(name = "idx_new_payment_id", columnList = "new_payment_event_id"),
    @Index(name = "idx_outcome_status", columnList = "outcome_status"),
    @Index(name = "idx_recovery_success", columnList = "recovery_success"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_action_id", nullable = false)
    private RecoveryAction recoveryAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_event_id", nullable = false)
    private PaymentEvent originalPaymentEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_payment_event_id")
    private PaymentEvent newPaymentEvent;

    @Column(nullable = false)
    @Convert(converter = OutcomeStatusConverter.class)
    private OutcomeStatus outcomeStatus = OutcomeStatus.PENDING;

    @Column
    private Boolean recoverySuccess;

    @Column(precision = 10, scale = 2)
    private BigDecimal recoveredAmount;

    @Column
    private Integer recoveryAttemptCount = 1;

    @Column
    private Integer timeToRecovery;

    @Column(precision = 5, scale = 2)
    private BigDecimal recoveryVelocityHours;

    @Column(columnDefinition = "JSON")
    private String feedbackNotes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
