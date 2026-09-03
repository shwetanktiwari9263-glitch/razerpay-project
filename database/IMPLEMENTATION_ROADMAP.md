# RecoverFlow: Implementation Roadmap

## Complete Data Flow Integration for Buildathon

This document maps the database schema to the actual code components you need to build during the 3-day buildathon.

---

## Architecture Layers

```
┌─────────────────────────────────────────────────┐
│  RAZORPAY WEBHOOKS                              │
│  (payment.failed, payment.captured)             │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│  WEBHOOK CONTROLLER (HealthController → extend) │
│  POST /api/webhooks/payment                     │
│  - Validate webhook signature                   │
│  - Store in payment_events table                │
└─────────────────┬───────────────────────────────┘
                  │
        ┌─────────┴──────────┐
        │                    │
    FAILED            CAPTURED
        │                    │
    ▼───┴─────────────────────▼──────────────────┐
    │ RECOVERY PIPELINE (if status = failed)     │
    └────────────────────────────────────────────┘
        │
    ┌───▼──────────────────────────────────────┐
    │ 1. ML MODEL / DEGRADATION ENGINE SERVICE  │
    │ → Analyze payment failure                 │
    │ → Populate system_insights table          │
    │ → Calculate failure_probability           │
    └───┬──────────────────────────────────────┘
        │
    ┌───▼──────────────────────────────────────┐
    │ 2. AI AGENT SERVICE                       │
    │ (Groq or hardcoded rules fallback)        │
    │ → Generate recovery strategy              │
    │ → Populate ai_agent_analysis table        │
    │ → Suggest channel (retry/link/notify)     │
    └───┬──────────────────────────────────────┘
        │
    ┌───▼──────────────────────────────────────┐
    │ 3. RULES ENGINE SERVICE                   │
    │ → Apply business rules                    │
    │ → Execute recovery_actions                │
    │ → Send payment link / trigger retry       │
    └───┬──────────────────────────────────────┘
        │
    ┌───▼──────────────────────────────────────┐
    │ 4. FEEDBACK LOOP                          │
    │ Razorpay sends payment.captured webhook   │
    │ → Link to recovery_outcomes table         │
    │ → Mark recovery_success = TRUE            │
    │ → Calculate time_to_recovery              │
    └───────────────────────────────────────────┘
```

---

## Implementation Tasks (Buildathon Sprint)

### Day 1: Foundation

#### Task 1.1: Create Entity Classes
**Files to create:**
- `backend/src/main/java/com/recoveryagent/entity/PaymentEvent.java`
- `backend/src/main/java/com/recoveryagent/entity/SystemInsight.java`
- `backend/src/main/java/com/recoveryagent/entity/AiAgentAnalysis.java`
- `backend/src/main/java/com/recoveryagent/entity/RecoveryAction.java`
- `backend/src/main/java/com/recoveryagent/entity/RecoveryOutcome.java`

**Reference:** See `ENTITY_TEMPLATES.md`

#### Task 1.2: Create Repository Interfaces
**Files to create:**
- `backend/src/main/java/com/recoveryagent/repository/PaymentEventRepository.java`
- `backend/src/main/java/com/recoveryagent/repository/SystemInsightRepository.java`
- `backend/src/main/java/com/recoveryagent/repository/AiAgentAnalysisRepository.java`
- `backend/src/main/java/com/recoveryagent/repository/RecoveryActionRepository.java`
- `backend/src/main/java/com/recoveryagent/repository/RecoveryOutcomeRepository.java`

**Example:**
```java
@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    Optional<PaymentEvent> findByEventId(String eventId);
    Optional<PaymentEvent> findByPaymentId(String paymentId);
    List<PaymentEvent> findByStatusAndCreatedAtAfter(
        PaymentStatus status, LocalDateTime date);
}
```

#### Task 1.3: Update application.properties for MySQL
**Modify:** `backend/src/main/resources/application-mysql.properties`

```properties
# MySQL Connection
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/recoverflow}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:password}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA / Hibernate
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true

# Connection Pool
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
```

**Note:** Default profile (mvn spring-boot:run) still works without MySQL.
**With MySQL:** mvn spring-boot:run -Dspring-boot.run.profiles=mysql

---

### Day 2: Webhook Handler & ML Mock

#### Task 2.1: Webhook Controller
**File:** `backend/src/main/java/com/recoveryagent/controller/WebhookController.java`

```java
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    @Autowired
    private PaymentEventService paymentEventService;

    @PostMapping("/payment")
    public ResponseEntity<?> handlePaymentWebhook(@RequestBody String payload) {
        // 1. Verify Razorpay webhook signature
        // 2. Parse JSON payload
        // 3. Save to payment_events table
        // 4. If status = 'failed', trigger recovery pipeline
        // 5. If status = 'captured', check if this is recovery payment
        
        return ResponseEntity.ok().build();
    }
}
```

#### Task 2.2: Payment Event Service
**File:** `backend/src/main/java/com/recoveryagent/service/PaymentEventService.java`

```java
@Service
public class PaymentEventService {
    
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    
    @Autowired
    private SystemInsightService systemInsightService;
    
    @Autowired
    private AiAgentService aiAgentService;
    
    @Autowired
    private RecoveryActionService recoveryActionService;
    
    public PaymentEvent processWebhook(WebhookPayload payload) {
        // Check for duplicate (idempotent via event_id)
        Optional<PaymentEvent> existing = 
            paymentEventRepository.findByEventId(payload.getEventId());
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Save new payment event
        PaymentEvent event = new PaymentEvent();
        event.setEventId(payload.getEventId());
        event.setPaymentId(payload.getPayment().getId());
        event.setOrderId(payload.getPayment().getOrderId());
        event.setAmount(new BigDecimal(payload.getPayment().getAmount()));
        event.setStatus(mapStatus(payload.getPayment().getStatus()));
        event.setRawPayload(payload.toString());
        
        PaymentEvent saved = paymentEventRepository.save(event);
        
        // Trigger recovery pipeline if failed
        if (saved.getStatus() == PaymentStatus.FAILED) {
            systemInsightService.analyzeFailure(saved);
        }
        
        return saved;
    }
    
    public void recordRecovery(String originalPaymentId, String capturedPaymentId) {
        // Called when payment.captured webhook arrives
        // Find recovery_action for originalPaymentId
        // Update recovery_outcomes with success status
    }
}
```

#### Task 2.3: System Insight Service (ML Mock)
**File:** `backend/src/main/java/com/recoveryagent/service/SystemInsightService.java`

```java
@Service
public class SystemInsightService {
    
    @Autowired
    private SystemInsightRepository systemInsightRepository;
    
    public SystemInsight analyzeFailure(PaymentEvent paymentEvent) {
        // MOCK ML MODEL OUTPUT
        // During buildathon, use hardcoded logic or random scores
        
        SystemInsight insight = new SystemInsight();
        insight.setPaymentEvent(paymentEvent);
        
        // Mock: Check error_code to categorize failure
        if (paymentEvent.getErrorCode().contains("gateway")) {
            insight.setRootCauseCategory(RootCauseCategory.GATEWAY_ISSUE);
            insight.setDegradationStatus(DegradationStatus.DEGRADED);
            insight.setFailureProbability(new BigDecimal("0.65"));
        } else if (paymentEvent.getErrorCode().contains("bank")) {
            insight.setRootCauseCategory(RootCauseCategory.BANK_ISSUE);
            insight.setFailureProbability(new BigDecimal("0.48"));
        } else {
            insight.setRootCauseCategory(RootCauseCategory.CUSTOMER_ISSUE);
            insight.setFailureProbability(new BigDecimal("0.82"));
        }
        
        insight.setModelVersion("mock-v1");
        insight.setModelConfidence(new BigDecimal("0.72"));
        
        return systemInsightRepository.save(insight);
    }
}
```

---

### Day 3: AI Agent, Rules Engine & Outcome Loop

#### Task 3.1: AI Agent Service (Groq or rule-based fallback)
**File:** `backend/src/main/java/com/recoveryagent/service/AiAgentService.java`

```java
@Service
public class AiAgentService {
    
    @Autowired
    private AiAgentAnalysisRepository aiAgentAnalysisRepository;
    
    @Autowired
    private SystemInsightRepository systemInsightRepository;
    
    public AiAgentAnalysis generateStrategy(PaymentEvent paymentEvent) {
        // MOCK AI DECISION LOGIC
        
        AiAgentAnalysis analysis = new AiAgentAnalysis();
        analysis.setPaymentEvent(paymentEvent);
        
        SystemInsight insight = systemInsightRepository
            .findByPaymentEvent(paymentEvent)
            .orElse(null);
        
        analysis.setSystemInsight(insight);
        analysis.setConfidenceScore(new BigDecimal("0.78"));
        
        // Simple rule-based strategy
        if (insight != null && insight.getFailureProbability().compareTo(new BigDecimal("0.7")) > 0) {
            analysis.setSuggestedChannel(RecoveryChannel.PAYMENT_LINK);
            analysis.setGeneratedExplanation(
                "High failure probability detected. Sending new payment link with 2-hour validity.");
            analysis.setRootCauseAnalysis(
                "Failure reason: " + insight.getRootCauseCategory() + 
                ". Recommended action: Send fresh payment link.");
        } else {
            analysis.setSuggestedChannel(RecoveryChannel.RETRY);
            analysis.setGeneratedExplanation("Initiating automatic retry in 1 hour.");
        }
        
        analysis.setAiModelName("recoverflow-rules-v1");
        analysis.setReasoningChain("[{\"step\":1,\"decision\":\"...\"}, ...]");
        
        return aiAgentAnalysisRepository.save(analysis);
    }
}
```

#### Task 3.2: Rules Engine Service
**File:** `backend/src/main/java/com/recoveryagent/service/RecoveryActionService.java`

```java
@Service
public class RecoveryActionService {
    
    @Autowired
    private RecoveryActionRepository recoveryActionRepository;
    
    @Autowired
    private AiAgentAnalysisRepository aiAgentAnalysisRepository;
    
    public RecoveryAction executeRecovery(AiAgentAnalysis analysis) {
        // Apply business rules and execute action
        
        RecoveryAction action = new RecoveryAction();
        action.setAiAgentAnalysis(analysis);
        action.setPaymentEvent(analysis.getPaymentEvent());
        action.setActionType(analysis.getSuggestedChannel());
        action.setExecutionStatus(ExecutionStatus.TRIGGERED);
        
        try {
            if (analysis.getSuggestedChannel() == RecoveryChannel.PAYMENT_LINK) {
                // Create new payment link via Razorpay API (mocked for now)
                String newLinkId = createPaymentLink(analysis.getPaymentEvent());
                action.setNewRazorpayLinkId(newLinkId);
                action.setExecutionStatus(ExecutionStatus.EXECUTED);
                
            } else if (analysis.getSuggestedChannel() == RecoveryChannel.RETRY) {
                // Trigger automatic retry
                String newPaymentId = triggerRetry(analysis.getPaymentEvent());
                action.setNewPaymentId(newPaymentId);
                action.setExecutionStatus(ExecutionStatus.EXECUTED);
            }
            
            action.setRulesApplied("RULE_HIGH_PRIORITY_RECOVERY, RULE_CUSTOMER_TIER_A");
            
        } catch (Exception e) {
            action.setExecutionStatus(ExecutionStatus.FAILED);
            action.setExecutionError(e.getMessage());
        }
        
        return recoveryActionRepository.save(action);
    }
    
    private String createPaymentLink(PaymentEvent event) {
        // Mock Razorpay createPaymentLink API call
        return "plink_" + UUID.randomUUID().toString();
    }
    
    private String triggerRetry(PaymentEvent event) {
        // Mock automatic retry
        return "pay_" + UUID.randomUUID().toString();
    }
}
```

#### Task 3.3: Recovery Outcome Service (Feedback Loop)
**File:** `backend/src/main/java/com/recoveryagent/service/RecoveryOutcomeService.java`

```java
@Service
public class RecoveryOutcomeService {
    
    @Autowired
    private RecoveryOutcomeRepository recoveryOutcomeRepository;
    
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    
    @Autowired
    private RecoveryActionRepository recoveryActionRepository;
    
    public void recordRecoverySuccess(String originalPaymentId, String capturedPaymentId) {
        // Called when payment.captured webhook arrives
        
        Optional<PaymentEvent> originalOpt = 
            paymentEventRepository.findByPaymentId(originalPaymentId);
        
        if (!originalOpt.isPresent()) return;
        
        PaymentEvent originalEvent = originalOpt.get();
        
        // Find recovery action for this original payment
        Optional<RecoveryAction> actionOpt = 
            recoveryActionRepository.findByPaymentEvent(originalEvent)
                .stream().findFirst();
        
        if (!actionOpt.isPresent()) return;
        
        RecoveryAction action = actionOpt.get();
        
        // Find or create recovery outcome
        Optional<RecoveryOutcome> outcomeOpt = 
            recoveryOutcomeRepository.findByRecoveryAction(action);
        
        RecoveryOutcome outcome = outcomeOpt.orElse(new RecoveryOutcome());
        outcome.setRecoveryAction(action);
        outcome.setOriginalPaymentEvent(originalEvent);
        
        Optional<PaymentEvent> capturedEvent = 
            paymentEventRepository.findByPaymentId(capturedPaymentId);
        
        if (capturedEvent.isPresent()) {
            outcome.setNewPaymentEvent(capturedEvent.get());
            outcome.setRecoverySuccess(true);
            outcome.setOutcomeStatus(OutcomeStatus.SUCCESS);
            outcome.setRecoveredAmount(originalEvent.getAmount());
            
            // Calculate time_to_recovery
            long secondsToRecover = Duration
                .between(action.getTriggeredAt(), LocalDateTime.now())
                .getSeconds();
            outcome.setTimeToRecovery((int) secondsToRecover);
        }
        
        recoveryOutcomeRepository.save(outcome);
    }
}
```

---

## Testing the Complete Flow

### Step 1: Start Backend (with MySQL profile)
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### Step 2: Simulate Razorpay Webhook (Payment Failed)
```bash
curl -X POST http://localhost:8080/api/webhooks/payment \
  -H "Content-Type: application/json" \
  -d '{
    "event": "payment.failed",
    "created_at": 1629802800,
    "contains": ["payment"],
    "payload": {
      "payment": {
        "entity": "payment",
        "id": "pay_29QQoUBi66xm2f",
        "entity_id": "order_9A33XWu170gUtm",
        "amount": 50000,
        "currency": "INR",
        "status": "failed",
        "method": "card",
        "error_code": "BAD_REQUEST_ERROR",
        "error_description": "Card declined"
      }
    }
  }'
```

### Step 3: Verify Database Entries
```sql
SELECT * FROM payment_events WHERE status = 'failed';
SELECT * FROM system_insights;
SELECT * FROM ai_agent_analysis;
SELECT * FROM recovery_actions;
```

### Step 4: Simulate Recovery Payment Captured Webhook
```bash
curl -X POST http://localhost:8080/api/webhooks/payment \
  -H "Content-Type: application/json" \
  -d '{
    "event": "payment.captured",
    "payload": {
      "payment": {
        "id": "pay_recovered_xyz",
        "order_id": "order_9A33XWu170gUtm",
        "amount": 50000,
        "status": "captured"
      }
    }
  }'
```

### Step 5: Check Recovery Outcome
```sql
SELECT * FROM recovery_outcomes WHERE recovery_success = TRUE;
SELECT * FROM v_recovery_pipeline WHERE order_id = 'order_9A33XWu170gUtm';
```

---

## Frontend Integration (Next Phase)

### Dashboard View (Post-Buildathon)
- Implement React component to fetch `/api/recovery/metrics`
- Display recovery rate, recovered amount, average recovery time
- Show real-time payment failures and recovery status

### Example Backend Endpoint
```java
@GetMapping("/api/recovery/metrics")
public ResponseEntity<?> getRecoveryMetrics(
    @RequestParam(required = false) LocalDate startDate,
    @RequestParam(required = false) LocalDate endDate) {
    
    return ResponseEntity.ok(recoveryOutcomeService.getMetrics(startDate, endDate));
}
```

---

## Deployment Checklist

- [ ] Create MySQL database: `mysql -u root -p < database/schema.sql`
- [ ] Set environment variables (DB_URL, DB_USERNAME, DB_PASSWORD)
- [ ] Run backend with mysql profile
- [ ] Verify webhook endpoint is accessible
- [ ] Test webhook signature validation
- [ ] Set up Razorpay test webhook endpoint
- [ ] Monitor logs for recovery pipeline execution
- [ ] Validate recovery_outcomes records on successful payments

---

## Success Criteria (Buildathon Demo)

✅ Backend receives Razorpay payment.failed webhook  
✅ System analyzes failure reason (mock ML)  
✅ AI agent generates recovery strategy  
✅ Rules engine executes recovery action (payment link)  
✅ Backend receives payment.captured webhook (recovery)  
✅ recovery_outcomes records success with time_to_recovery  
✅ Dashboard shows recovery metrics (recovery rate, avg time, amount recovered)  
✅ All 5 tables populated with data flowing through feedback loop
