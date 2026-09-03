package com.recoveryagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "recovery_actions", indexes = {
    @Index(name = "idx_ai_agent_analysis_id", columnList = "ai_agent_analysis_id"),
    @Index(name = "idx_payment_event_id", columnList = "payment_event_id"),
    @Index(name = "idx_new_payment_id", columnList = "new_payment_id"),
    @Index(name = "idx_execution_status", columnList = "execution_status"),
    @Index(name = "idx_action_type", columnList = "action_type"),
    @Index(name = "idx_triggered_at", columnList = "triggered_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_agent_analysis_id", nullable = false)
    private AiAgentAnalysis aiAgentAnalysis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_event_id", nullable = false)
    private PaymentEvent paymentEvent;

    @Column(nullable = false)
    @Convert(converter = RecoveryChannelConverter.class)
    private RecoveryChannel actionType;

    @Column(nullable = false)
    @Convert(converter = ExecutionStatusConverter.class)
    private ExecutionStatus executionStatus = ExecutionStatus.PENDING;

    @Column(length = 128)
    private String newRazorpayLinkId;

    @Column(length = 128)
    private String newPaymentId;

    @Column(columnDefinition = "JSON")
    private String actionMetadata;

    @Column(length = 255)
    private String rulesApplied;

    @Column(columnDefinition = "TEXT")
    private String executionError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime triggeredAt;

    @Column
    private LocalDateTime executedAt;

    @OneToMany(mappedBy = "recoveryAction", cascade = CascadeType.ALL)
    private Set<RecoveryOutcome> outcomes;
}
