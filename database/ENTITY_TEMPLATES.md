/* ============================================================================
   RecoverFlow Database Schema - Entity Class Sketches
   
   These are conceptual Spring Boot JPA entity outlines matching the schema.
   Use these as templates to implement the actual entity classes.
   ============================================================================ */

// ============================================================================
// 1. PaymentEvent
// ============================================================================
/*
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
    @Enumerated(EnumType.STRING)
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

public enum PaymentStatus {
    FAILED, CAPTURED, AUTHORIZED, REFUNDED, PENDING
}
*/

// ============================================================================
// 2. SystemInsight
// ============================================================================
/*
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
    @Enumerated(EnumType.STRING)
    private DegradationStatus degradationStatus = DegradationStatus.HEALTHY;
    
    @Column(length = 50)
    private String affectedGateway;
    
    @Column(length = 100)
    private String affectedBank;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
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

public enum DegradationStatus {
    HEALTHY, DEGRADED, CRITICAL
}

public enum RootCauseCategory {
    GATEWAY_ISSUE, BANK_ISSUE, CUSTOMER_ISSUE, NETWORK_ISSUE,
    RATE_LIMIT, TIMEOUT, INSUFFICIENT_FUNDS, INVALID_CARD, UNKNOWN
}
*/

// ============================================================================
// 3. AiAgentAnalysis
// ============================================================================
/*
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
    @Enumerated(EnumType.STRING)
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

public enum RecoveryChannel {
    RETRY, PAYMENT_LINK, NOTIFICATION, CUSTOMER_SUPPORT, NONE
}
*/

// ============================================================================
// 4. RecoveryAction
// ============================================================================
/*
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
    @Enumerated(EnumType.STRING)
    private RecoveryChannel actionType;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
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

public enum ExecutionStatus {
    TRIGGERED, PENDING, EXECUTED, FAILED, CANCELLED
}
*/

// ============================================================================
// 5. RecoveryOutcome
// ============================================================================
/*
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
    @Enumerated(EnumType.STRING)
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

public enum OutcomeStatus {
    SUCCESS, FAILED, PENDING, TIMEOUT, CANCELLED, PARTIAL
}
*/

// ============================================================================
// USAGE NOTES
// ============================================================================

/*
INSTRUCTIONS FOR IMPLEMENTATION:

1. Copy the above code blocks into separate files in:
   - backend/src/main/java/com/recoveryagent/entity/
   
2. Uncomment the code (remove /* and *)

3. Add missing imports:
   - jakarta.persistence.*
   - org.hibernate.annotations.*
   - com.fasterxml.jackson.annotation.* (for JSON fields)
   - java.time.LocalDateTime
   - java.math.BigDecimal
   - lombok.*
   
4. Configure Spring JPA to auto-generate DDL (optional for dev):
   - In application.properties: spring.jpa.hibernate.ddl-auto=create-drop (dev only)
   - Or: ddl-auto=validate (production)

5. Create repository interfaces:
   - @Repository interface extending JpaRepository<Entity, Long>

6. Example Repository:
   @Repository
   public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
       Optional<PaymentEvent> findByEventId(String eventId);
       List<PaymentEvent> findByStatusAndCreatedAtAfter(PaymentStatus status, LocalDateTime date);
   }

7. To handle JSON columns with proper serialization:
   - Use @Column(columnDefinition = "JSON")
   - Jackson will auto-serialize/deserialize to String/Object
   - For typed JSON, use custom converters or store as String and parse in service layer

8. Foreign Key Cascade Rules already configured in schema:
   - @ManyToOne fetch = FetchType.LAZY for performance
   - cascade = CascadeType.ALL on @OneToMany for referential integrity

9. Testing:
   - Use H2 in-memory database for unit tests
   - Use @DataJpaTest for repository tests
   - Mock PaymentEvent objects for service layer tests
*/
