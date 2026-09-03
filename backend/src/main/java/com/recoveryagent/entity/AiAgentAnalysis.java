package com.recoveryagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "ai_agent_analysis", indexes = {
    @Index(name = "idx_payment_event_id", columnList = "payment_event_id"),
    @Index(name = "idx_system_insight_id", columnList = "system_insight_id"),
    @Index(name = "idx_suggested_channel", columnList = "suggested_channel"),
    @Index(name = "idx_confidence_score", columnList = "confidence_score")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_event_id", nullable = false)
    private PaymentEvent paymentEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_insight_id")
    private SystemInsight systemInsight;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rootCauseAnalysis;

    @Column(columnDefinition = "TEXT")
    private String generatedExplanation;

    @Column(nullable = false)
    @Convert(converter = RecoveryChannelConverter.class)
    private RecoveryChannel suggestedChannel = RecoveryChannel.NONE;

    @Column(precision = 10, scale = 2)
    private BigDecimal suggestedAmount;

    @Column(columnDefinition = "JSON")
    private String retryStrategy;

    @Column(columnDefinition = "JSON")
    private String recoveryStrategy;

    @Column(length = 100)
    private String aiModelName;

    @Column(columnDefinition = "JSON")
    private String reasoningChain;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "aiAgentAnalysis", cascade = CascadeType.ALL)
    private Set<RecoveryAction> actions;
}
