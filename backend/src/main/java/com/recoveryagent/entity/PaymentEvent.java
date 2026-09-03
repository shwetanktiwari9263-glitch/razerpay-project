package com.recoveryagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "payment_events", indexes = {
    @Index(name = "idx_payment_id", columnList = "payment_id"),
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_status_created", columnList = "status,created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 128)
    private String eventId;

    @Column(nullable = false, length = 128)
    private String paymentId;

    @Column(length = 128)
    private String orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "INR";

    @Column(nullable = false)
    @Convert(converter = PaymentStatusConverter.class)
    private PaymentStatus status;

    @Column(length = 50)
    private String gateway;

    @Column(length = 100)
    private String bankOrWallet;

    @Column(length = 50)
    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorDescription;

    @Column(length = 128)
    private String customerId;

    @Column(columnDefinition = "JSON", nullable = false)
    private String rawPayload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "paymentEvent", cascade = CascadeType.ALL)
    private Set<SystemInsight> insights;

    @OneToMany(mappedBy = "paymentEvent", cascade = CascadeType.ALL)
    private Set<AiAgentAnalysis> analyses;

    @OneToMany(mappedBy = "paymentEvent", cascade = CascadeType.ALL)
    private Set<RecoveryAction> actions;

    @OneToMany(mappedBy = "originalPaymentEvent", cascade = CascadeType.ALL)
    private Set<RecoveryOutcome> outcomes;

    @OneToMany(mappedBy = "newPaymentEvent")
    private Set<RecoveryOutcome> recoveredOutcomes;
}
