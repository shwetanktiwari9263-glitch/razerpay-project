package com.recoveryagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_insights", indexes = {
    @Index(name = "idx_payment_event_id", columnList = "payment_event_id"),
    @Index(name = "idx_degradation_status", columnList = "degradation_status"),
    @Index(name = "idx_affected_gateway", columnList = "affected_gateway"),
    @Index(name = "idx_root_cause", columnList = "root_cause_category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_event_id", nullable = false)
    private PaymentEvent paymentEvent;

    @Column(precision = 5, scale = 4)
    private BigDecimal failureProbability;

    @Column(nullable = false)
    @Convert(converter = DegradationStatusConverter.class)
    private DegradationStatus degradationStatus = DegradationStatus.HEALTHY;

    @Column(length = 50)
    private String affectedGateway;

    @Column(length = 100)
    private String affectedBank;

    @Column(nullable = false)
    @Convert(converter = RootCauseCategoryConverter.class)
    private RootCauseCategory rootCauseCategory = RootCauseCategory.UNKNOWN;

    @Column(precision = 5, scale = 4)
    private BigDecimal modelConfidence;

    @Column(length = 20)
    private String modelVersion;

    @Column(columnDefinition = "JSON")
    private String additionalMetadata;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
