# RecoverFlow: Complete Architecture Diagram

## System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          RAZORPAY PAYMENT GATEWAY                           │
└────────────┬────────────────────────────────────────────────────────┬────────┘
             │                                                        │
      WEBHOOK: payment.failed                              WEBHOOK: payment.captured
             │                                                        │
             ▼                                                        ▼
      ┌──────────────┐                                     ┌──────────────────┐
      │   Backend    │                                     │  Backend         │
      │   Webhook    │                                     │  Webhook Handler │
      │   Handler    │                                     │  (recovery match)│
      └──────┬───────┘                                     └────────┬─────────┘
             │                                                      │
             ▼                                                      ▼
      ┌──────────────────────────────────────────────────────────────────────┐
      │                         MySQL Database                               │
      │                         (recoverflow)                                │
      │                                                                      │
      │  ┌─────────────────────────────────────────────────────────────┐   │
      │  │ TABLE: payment_events                                       │   │
      │  │ ├─ id: 1, payment_id, status='FAILED', raw_payload         │   │
      │  │ └─ id: 2, payment_id (recovered), status='CAPTURED'         │   │
      │  └──────────────┬──────────────────────────────────────────────┘   │
      │                 │                                                   │
      │  ┌──────────────▼──────────────────────────────────────────────┐   │
      │  │ TABLE: system_insights                                      │   │
      │  │ └─ payment_event_id=1, root_cause_category, failure_prob    │   │
      │  └──────────────┬──────────────────────────────────────────────┘   │
      │                 │                                                   │
      │  ┌──────────────▼──────────────────────────────────────────────┐   │
      │  │ TABLE: ai_agent_analysis                                    │   │
      │  │ └─ payment_event_id=1, suggested_channel='payment_link'     │   │
      │  └──────────────┬──────────────────────────────────────────────┘   │
      │                 │                                                   │
      │  ┌──────────────▼──────────────────────────────────────────────┐   │
      │  │ TABLE: recovery_actions                                     │   │
      │  │ └─ ai_agent_analysis_id=1, action_type, new_razorpay_link_id   │
      │  └──────────────┬──────────────────────────────────────────────┘   │
      │                 │                                                   │
      │                 ◄───────────────────────────────────────┐           │
      │                 │         FEEDBACK LOOP                 │           │
      │  ┌──────────────▼──────────────────────────────────────┴────────┐   │
      │  │ TABLE: recovery_outcomes                                     │   │
      │  │ ├─ recovery_action_id=1                                     │   │
      │  │ ├─ original_payment_event_id=1 (failed)                     │   │
      │  │ ├─ new_payment_event_id=2 (captured)                        │   │
      │  │ ├─ recovery_success=TRUE                                    │   │
      │  │ ├─ time_to_recovery=7200 (seconds)                          │   │
      │  │ └─ recovered_amount=500.00                                  │   │
      │  └──────────────────────────────────────────────────────────────┘   │
      │                                                                      │
      └──────────────────────────────────────────────────────────────────────┘
             │                                                      ▲
             │                                                      │
             └──────────────────────────────────────────────────────┘
                   BUSINESS LOGIC FLOW (Spring Boot Services)
```

---

## Payment Recovery Sequence Diagram

```
Razorpay    Backend         ML              AI           Rules        Recovery
 Payment    Webhook       Model           Agent         Engine       Outcomes
  Flow      Handler       (Mock)         Service       Service        Service
   │           │            │              │              │              │
   │           │            │              │              │              │
   ├──payment.failed──────►│            │              │              │
   │           │            │              │              │              │
   │           ├─INSERT payment_events────►│              │              │
   │           │            │              │              │              │
   │           ├──analyze────►│            │              │              │
   │           │            │              │              │              │
   │           │   ├─INSERT system_insights               │              │
   │           │            │              │              │              │
   │           ├──strategy───►│────────────►│            │              │
   │           │            │              │              │              │
   │           │            │  ├─INSERT ai_agent_analysis│              │
   │           │            │              │              │              │
   │           │            │              ├──execute─────►│            │
   │           │            │              │              │              │
   │           │            │              │  ├─INSERT recovery_actions  │
   │           │            │              │              │              │
   │           │            │              │              ├─Send Link    │
   │           │            │              │              │              │
   │  [CUSTOMER RE-ATTEMPTS PAYMENT]                      │              │
   │           │            │              │              │              │
   ├──payment.captured──────►│            │              │              │
   │           │            │              │              │              │
   │           ├─INSERT payment_events (CAPTURED)         │              │
   │           │            │              │              │              │
   │           ├────────────────────────────────────────────────────────►│
   │           │            │              │              │              │
   │           │            │              │              │  ├─UPDATE   │
   │           │            │              │              │  │ recovery_│
   │           │            │              │              │  │ outcomes │
   │           │            │              │              │  │          │
   │           │            │              │              │  │ success= │
   │           │            │              │              │  │ TRUE    ◄┘
   │           │            │              │              │              │
   │  [FEEDBACK LOOP COMPLETE]                           │              │
```

---

## Data Structure Hierarchy

```
payment_events (ROOT)
│
├─► system_insights
│   ├─ Stores: ML failure analysis
│   ├─ Fields: root_cause_category, failure_probability, degradation_status
│   └─ Purpose: Why did payment fail?
│
├─► ai_agent_analysis
│   ├─ Stores: AI decision-making
│   ├─ Fields: suggested_channel, confidence_score, recovery_strategy
│   └─ Purpose: How should we recover it?
│
├─► recovery_actions
│   ├─ Stores: Executed recovery action
│   ├─ Fields: action_type, execution_status, new_razorpay_link_id
│   └─ Purpose: What action was taken?
│
└─► recovery_outcomes
    ├─ Stores: Final result
    ├─ Fields: recovery_success, recovered_amount, time_to_recovery
    ├─ Links Back: new_payment_event_id → payment_events (CAPTURED)
    └─ Purpose: Did the recovery work?
```

---

## Foreign Key Relationships

```
┌──────────────────┐
│ payment_events   │ (status = 'failed')
│ id: 1            │
└────────┬─────────┘
         │
         │ FK: payment_event_id
         │
         ▼
┌──────────────────────┐
│ system_insights      │
│ id: 1                │
└────────┬─────────────┘
         │
         │ FK: system_insight_id (optional)
         │
         ▼
┌──────────────────────┐
│ ai_agent_analysis    │
│ id: 1                │
└────────┬─────────────┘
         │
         │ FK: ai_agent_analysis_id
         │
         ▼
┌──────────────────────┐
│ recovery_actions     │
│ id: 1                │
└────────┬─────────────┘
         │
         │ FK: recovery_action_id
         │
         ▼
┌──────────────────────┐
│ recovery_outcomes    │
│ id: 1                │
│                      │
│ FK: new_payment_     │
│     event_id ────────┼──────────┐
└──────────────────────┘          │
                                  │
                        ┌─────────▼───────────┐
                        │ payment_events      │
                        │ id: 2               │
                        │ status: 'captured'  │
                        └─────────────────────┘
```

---

## Component Responsibilities

### 1. Backend Webhook Controller
```
Responsibility: Receive and parse Razorpay webhooks
Input: POST /api/webhooks/payment with JSON payload
Output: Insert into payment_events table
Actions:
  ├─ Validate webhook signature
  ├─ Check event_id for idempotency
  ├─ Parse payment details
  └─ Trigger recovery pipeline if payment.failed
```

### 2. System Insights Service (ML Mock)
```
Responsibility: Analyze payment failure reasons
Input: PaymentEvent with error_code, gateway, bank
Output: Insert into system_insights table
Logic:
  ├─ Parse error_code
  ├─ Categorize root_cause (gateway_issue, bank_issue, customer_issue, etc.)
  ├─ Calculate failure_probability (0-1)
  └─ Determine degradation_status (healthy, degraded, critical)
```

### 3. AI Agent Service (Groq or rule-based fallback)
```
Responsibility: Suggest recovery strategy
Input: PaymentEvent + SystemInsight
Output: Insert into ai_agent_analysis table
Logic:
  ├─ Analyze root_cause_category
  ├─ Evaluate customer history
  ├─ Select suggested_channel (retry, payment_link, notification, none)
  ├─ Calculate confidence_score
  └─ Generate explanation for dashboard
```

### 4. Rules Engine Service
```
Responsibility: Execute recovery action
Input: AiAgentAnalysis with suggested_channel
Output: Insert into recovery_actions table
Logic:
  ├─ Apply business rules (time limits, amount thresholds, etc.)
  ├─ Execute action based on suggested_channel
  │  ├─ retry: trigger automatic retry via Razorpay API
  │  ├─ payment_link: create new payment link, send SMS/email
  │  └─ notification: send reminder notification
  └─ Track execution_status (triggered, executed, failed)
```

### 5. Recovery Outcome Service (Feedback Loop)
```
Responsibility: Close the feedback loop
Input: payment.captured webhook with recovered payment_id
Output: Insert into recovery_outcomes table
Logic:
  ├─ Find corresponding recovery_action
  ├─ Link new payment_event_id (captured payment)
  ├─ Set recovery_success = TRUE
  ├─ Calculate time_to_recovery
  └─ Update dashboard metrics
```

---

## State Machines

### Payment Event State Transitions
```
┌────────┐
│ FAILED │ ──────────────┬─────────────────┐
└────────┘               │                 │
                    RECOVERY            NO RECOVERY
                    ATTEMPTS            (timeout, cancel)
                         │                 │
                         ▼                 ▼
                   ┌──────────┐      ┌──────────┐
                   │ RECOVERY │      │  FAILED  │
                   │ PENDING  │      │ (final)  │
                   └────┬─────┘      └──────────┘
                        │
                   customer retries
                        │
                        ▼
                   ┌──────────┐
                   │CAPTURED  │
                   │(success!)│
                   └──────────┘
```

### Recovery Action State Transitions
```
┌──────────────┐
│  TRIGGERED   │ ──────────────────────┐
└──────────────┘                        │
       │                                │
    ┌──┴──┐                             │
    │     │                             │
    ▼     ▼                             ▼
┌─────────────┐                   ┌────────┐
│   PENDING   │                   │ FAILED │
│(awaiting    │                   │(exec   │
│execution)   │                   │failed) │
└──────┬──────┘                   └────────┘
       │
    executes
       │
       ▼
┌──────────────┐
│  EXECUTED    │
│(waiting for  │
│customer to   │
│complete      │
│payment)      │
└──────────────┘
```

### Recovery Outcome State Transitions
```
┌──────────┐
│ PENDING  │ ────┬────────────┬──────────┐
└──────────┘     │            │          │
                 │            │          │
            success       failed    timeout/cancel
                 │            │          │
                 ▼            ▼          ▼
             ┌────────┐  ┌────────┐  ┌──────────┐
             │SUCCESS │  │ FAILED │  │ TIMEOUT  │
             │(payment│  │(recovery │ │(no      │
             │captured)  │action   │ │response) │
             │        │  │failed)  │ │          │
             └────────┘  └────────┘  └──────────┘
```

---

## Performance & Scalability Considerations

### Indexing Strategy
```
HOT QUERIES:
├─ Find failed payments: payment_events (status, created_at)
├─ Filter by degradation: system_insights (degradation_status)
├─ Group by channel: ai_agent_analysis (suggested_channel)
├─ Check execution status: recovery_actions (execution_status)
└─ Dashboard metrics: recovery_outcomes (recovery_success, created_at)

INDEXES CONFIGURED:
├─ Single column: status, created_at, degradation_status, action_type
├─ Composite: (status, created_at), (payment_event_id, execution_status)
└─ Unique: event_id (prevents duplicate webhooks)
```

### Cascade Rules
```
ON DELETE CASCADE:
├─ payment_events → system_insights (analysis cascade)
├─ payment_events → ai_agent_analysis (strategy cascade)
├─ payment_events → recovery_actions (action cascade)
└─ payment_events → recovery_outcomes (outcome cascade)

Purpose: Cleanup entire recovery pipeline when payment_event deleted

ON DELETE SET NULL:
└─ recovery_outcomes.new_payment_event_id (preserve outcome if captured payment deleted)

Purpose: Keep recovery records for audit even if source deleted
```

---

## Data Volume Projections (Annual)

```
Assumption: 10,000 payments/day, 2% failure rate = 200 failures/day

Annual Volumes:
├─ payment_events: 3,650,000 rows (all payments)
├─ system_insights: 73,000 rows (only failures)
├─ ai_agent_analysis: 73,000 rows (one analysis per failure)
├─ recovery_actions: 58,400 rows (80% of failures get action)
└─ recovery_outcomes: 58,400 rows (1:1 with recovery_actions)

Storage Estimation:
├─ payment_events: ~3.6 GB (with raw_payload JSON)
├─ system_insights: ~70 MB
├─ ai_agent_analysis: ~150 MB
├─ recovery_actions: ~120 MB
└─ recovery_outcomes: ~140 MB
│
└─ TOTAL: ~4.0 GB/year (manageable, single instance)

Recommendations for Scale:
├─ Monthly partitioning after 2 years
├─ Archive to cold storage after 1 year
├─ Read replicas for analytics
└─ Caching layer (Redis) for dashboard metrics
```

---

## Security & Audit Trail

```
Auditability:
├─ raw_payload: Complete webhook stored for dispute resolution
├─ reasoning_chain: AI step-by-step decision stored as JSON
├─ rules_applied: Which business rules triggered action
├─ created_at, updated_at: Timestamps on all tables
└─ execution_error: Failure reasons captured

Data Protection:
├─ Foreign key constraints prevent orphaned records
├─ UNIQUE event_id prevents duplicate webhook processing
├─ Cascade rules ensure data consistency
└─ JSON fields encrypted at application layer (future)

Access Control (Future):
├─ Role-based table access (finance, support, operations)
├─ Audit logging on sensitive data changes
├─ Webhook signature verification
└─ API rate limiting on webhook handler
```

---

## Deployment Topology

```
┌─────────────────────────────────────────────────────────┐
│                    PRODUCTION (AWS/Cloud)               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────────┐         ┌──────────────────────────┐  │
│  │  ALB / LB   │         │   Spring Boot Backend    │  │
│  │ (HTTPS)     │◄────────┤   (ECS / Docker)         │  │
│  └────────┬────┘         │   ├─ Webhook Handler     │  │
│           │              │   ├─ Services            │  │
│           │              │   └─ Repositories        │  │
│           │              └──────┬───────────────────┘  │
│           │                     │                      │
│  Razorpay ├─────────────────────┘                     │
│ Webhooks  │                                            │
│           │              ┌──────────────────────────┐  │
│           └─────────────►│    MySQL Database        │  │
│                          │  (RDS Multi-AZ)         │  │
│                          │  ├─ recoverflow DB       │  │
│                          │  ├─ 5 tables            │  │
│                          │  └─ Automated backups    │  │
│                          └──────────────────────────┘  │
│                                                         │
│  ┌──────────────────────┐      ┌──────────────────┐   │
│  │  React Frontend      │      │  CloudFront CDN  │   │
│  │  (Vercel / S3)       │◄─────┤  (Static assets) │   │
│  │  ├─ Dashboard        │      └──────────────────┘   │
│  │  ├─ Metrics          │                             │
│  │  └─ Recovery Status  │                             │
│  └──────────────────────┘                             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## Success Metrics (Buildathon Demo Target)

```
✓ Database created with all 5 tables
✓ Webhook received and stored in payment_events
✓ ML mock analysis stored in system_insights
✓ AI strategy generated in ai_agent_analysis
✓ Recovery action executed in recovery_actions
✓ Recovery payment linked in recovery_outcomes
✓ Feedback loop closed (success = TRUE)
✓ Recovery metrics calculated (time_to_recovery, recovered_amount)
✓ Dashboard displays recovery rate >= 40%
✓ Code structured for Groq/rule-based analysis and Razorpay API integration
```
