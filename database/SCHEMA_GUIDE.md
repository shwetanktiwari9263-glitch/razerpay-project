# RecoverFlow Database Schema Guide

## Overview

This production-ready MySQL schema powers the AI Revenue Recovery Agent for the Razorpay AI Buildathon. It captures the complete feedback loop from payment failure through AI-driven recovery to outcome tracking.

## Data Flow Architecture

```
Razorpay Webhooks
        ↓
payment_events (raw payment failure)
        ↓
system_insights (ML Model analyzes why payment failed)
        ↓
ai_agent_analysis (AI Agent suggests recovery strategy)
        ↓
recovery_actions (Rules Engine executes the action)
        ↓
recovery_outcomes (Feedback loop: track if recovery succeeded)
        ↓
new payment_events (payment.captured webhook) → linked via recovery_outcomes
```

---

## Table Details

### 1. payment_events
**Purpose:** Stores raw incoming payment webhooks from Razorpay.

**Key Fields:**
- `event_id` (UNIQUE): Razorpay's webhook event ID for idempotent processing
- `payment_id`, `order_id`: Razorpay identifiers
- `status`: ENUM (failed, captured, authorized, refunded, pending)
- `raw_payload` (JSON): Complete webhook data for audit and re-processing
- `error_code`, `error_description`: Razorpay error details
- `gateway`, `bank_or_wallet`: Identifies which provider failed

**Indexes:**
- Composite index on (status, created_at) for fast failed payment queries
- Single indexes on payment_id, order_id for transaction lookups

**Design Notes:**
- Stores both failed and successful payments
- `captured` status payments can be linked back to recovery attempts via recovery_outcomes

---

### 2. system_insights
**Purpose:** Stores ML Model and Degradation Engine analysis.

**Key Fields:**
- `failure_probability` (0-1): Score indicating reoccurrence risk
- `root_cause_category` (ENUM): Gateway issue, bank issue, customer issue, rate limit, timeout, insufficient funds, invalid card, unknown
- `degradation_status`: healthy, degraded, critical
- `affected_gateway`, `affected_bank`: Specific provider experiencing issues
- `additional_metadata` (JSON): Flexible storage for model-specific insights

**Indexes:**
- FK to payment_events for join performance
- Separate indexes on degradation_status and root_cause_category for filtering

**Design Notes:**
- 1:1 relationship with payment_events (one insight per payment)
- Mocked ML model output during buildathon; structure ready for real models
- JSON metadata supports flexible ML outputs (retry_wait_duration, backoff_strategy, etc.)

---

### 3. ai_agent_analysis
**Purpose:** Stores AI Agent's root cause analysis and recovery strategy.

**Key Fields:**
- `confidence_score` (0-1): AI's confidence in the suggested strategy
- `root_cause_analysis` (TEXT): Detailed RCA explanation
- `suggested_channel` (ENUM): retry, payment_link, notification, customer_support, none
- `retry_strategy` (JSON): Contains retry_count, retry_delay_seconds, backoff_multiplier
- `recovery_strategy` (JSON): Full strategy object (timing, conditions, fallbacks)
- `reasoning_chain` (JSON): Step-by-step AI reasoning for explainability

**Foreign Keys:**
- → payment_events (1:1 analysis per payment)
- → system_insights (optional: uses ML insights as input)

**Indexes:**
- FK indexes for join performance
- Index on suggested_channel for grouping by action type

**Design Notes:**
- Bridge between ML insights and rules engine
- JSON fields store structured strategy for audit trails
- Reasoning chain enables explainable AI requirement
- Allows multiple analyses per payment if needed in future

---

### 4. recovery_actions
**Purpose:** Tracks what the Rules Engine decided to execute.

**Key Fields:**
- `action_type` (ENUM): retry, payment_link, notification, customer_support, none
- `execution_status` (ENUM): triggered, pending, executed, failed, cancelled
- `new_razorpay_link_id`: Payment link ID if sending link to customer
- `new_payment_id`: New payment ID if automatic retry
- `rules_applied`: Comma-separated list of business rules that triggered
- `action_metadata` (JSON): Action-specific data (link_expiry_ts, sms_template_id, retry_count, etc.)

**Foreign Keys:**
- → ai_agent_analysis (rules execute based on AI strategy)
- → payment_events (link to original failed payment)

**Indexes:**
- Composite index on (payment_event_id, execution_status) for fast status queries
- Separate index on new_payment_id for linking to recovery payments

**Design Notes:**
- Separates AI decision from actual execution
- Tracks execution errors for debugging failed actions
- Metadata JSON allows action-specific parameters without schema changes

---

### 5. recovery_outcomes
**Purpose:** Completes the feedback loop by tracking final outcomes.

**Key Fields:**
- `recovery_success` (BOOLEAN): TRUE if payment.captured received after action
- `recovered_amount`: Amount of successful recovery
- `outcome_status` (ENUM): success, failed, pending, timeout, cancelled, partial
- `time_to_recovery` (seconds): Duration from action trigger to success
- `recovery_attempt_count`: How many times recovery was attempted
- `recovery_velocity_hours`: Hours between original failure and recovery
- `new_payment_event_id` (FK): Links to the successful payment.captured webhook event

**Foreign Keys:**
- → recovery_actions (which action this outcome belongs to)
- → payment_events (both original failed payment and successful recovery payment via new_payment_event_id)

**Indexes:**
- Full index coverage for dashboard queries
- recovery_success index for quick success/failure filtering
- Composite indexes for time-series analysis

**Design Notes:**
- **The Feedback Loop:** When Razorpay sends `payment.captured` webhook for a recovery payment:
  1. Create new row in payment_events (status='captured')
  2. Link it to recovery_outcomes via new_payment_event_id
  3. Set recovery_success=TRUE
  4. Update outcome_status='success'
  5. Calculate time_to_recovery = (captured_at - triggered_at)

---

## Foreign Key Relationships (Feedback Loop)

```
payment_events (failed)
    ↓ FK: payment_event_id
system_insights
    ↓ FK: system_insight_id (optional)
ai_agent_analysis
    ↓ FK: ai_agent_analysis_id
recovery_actions
    ↓ FK: recovery_action_id
recovery_outcomes
    ↓ FK: new_payment_event_id (points back to payment_events)
payment_events (captured - successful recovery)
```

**Cascade Rules:**
- DELETE on payment_events cascades to all downstream tables (system_insights, ai_agent_analysis, recovery_actions, recovery_outcomes)
- new_payment_event_id uses SET NULL on DELETE to preserve outcome records even if successful payment is deleted

---

## Analytics Views

### v_recovery_pipeline
Joins all 5 tables to show complete recovery journey for a specific order.

**Use Case:**
```sql
SELECT * FROM v_recovery_pipeline 
WHERE order_id = 'order_xyz'
ORDER BY failure_time DESC;
```

Returns one row per recovery attempt with all details visible.

### v_recovery_metrics
Aggregated daily metrics: recovery rate, volume, amount recovered.

**Use Case:**
```sql
SELECT * FROM v_recovery_metrics 
WHERE recovery_date BETWEEN DATE_SUB(NOW(), INTERVAL 30 DAY) AND CURDATE()
ORDER BY recovery_date DESC;
```

Returns daily success rate % and recovered amount trending.

---

## Implementation Notes

### JSON Field Strategy
Uses JSON columns for flexible, nested data:
- `raw_payload`: Entire Razorpay webhook (future: re-process with new logic)
- `additional_metadata`: ML model-specific insights
- `recovery_strategy`: Structured strategy with conditions/timing
- `action_metadata`: Action-specific parameters

**Advantage:** No schema migration needed for new fields during buildathon.

### Performance Optimizations
- **Composite indexes** on frequently joined columns
- **Selective indexes** on filter columns (status, degradation_status, outcome_status)
- **InnoDB storage engine** with ACID compliance
- **UTF8MB4 charset** for international customer data support

### Auditability
- All tables have `created_at` and `updated_at` timestamps
- `reasoning_chain` in ai_agent_analysis for AI explainability
- `rules_applied` in recovery_actions tracks which business rules fired
- `raw_payload` preserved for dispute resolution

---

## Quick Start: Executing the Schema

```bash
# Connect to MySQL
mysql -u root -p

# Paste the entire schema.sql file content
```

Or:

```bash
mysql -u your_user -p recoverflow < database/schema.sql
```

---

## Testing the Feedback Loop

### Step 1: Insert a failed payment
```sql
INSERT INTO payment_events (event_id, payment_id, order_id, amount, status, raw_payload)
VALUES ('evt_123', 'pay_456', 'order_789', 500.00, 'failed', 
        '{"error_code":"BAD_REQUEST_ERROR","description":"Card declined"}');
```

### Step 2: Analyze with ML insights
```sql
INSERT INTO system_insights (payment_event_id, failure_probability, root_cause_category)
VALUES (1, 0.78, 'bank_issue');
```

### Step 3: AI generates strategy
```sql
INSERT INTO ai_agent_analysis 
(payment_event_id, system_insight_id, confidence_score, root_cause_analysis, suggested_channel)
VALUES (1, 1, 0.85, 'Bank declined due to insufficient funds. Retry after 2 hours.', 'payment_link');
```

### Step 4: Rules Engine executes
```sql
INSERT INTO recovery_actions 
(ai_agent_analysis_id, payment_event_id, action_type, execution_status, new_razorpay_link_id)
VALUES (1, 1, 'payment_link', 'triggered', 'plink_abc123');
```

### Step 5: Customer completes payment → webhook arrives
```sql
INSERT INTO payment_events (event_id, payment_id, order_id, amount, status, raw_payload)
VALUES ('evt_captured_999', 'pay_456_captured', 'order_789', 500.00, 'captured', '{}');
```

### Step 6: Outcome recorded (feedback loop complete)
```sql
INSERT INTO recovery_outcomes 
(recovery_action_id, original_payment_event_id, new_payment_event_id, 
 outcome_status, recovery_success, time_to_recovery)
VALUES (1, 1, 2, 'success', TRUE, 7200);
```

---

## Next Steps for the Buildathon

1. **Webhook Handler:** Create Spring Boot endpoint to receive Razorpay webhooks and insert into payment_events
2. **ML Mock:** Implement system_insights population (can be random scores during buildathon)
3. **AI Agent:** Implement ai_agent_analysis generation (Groq calls or hardcoded rules fallback)
4. **Rules Engine:** Implement recovery_actions trigger logic
5. **Outcome Tracking:** Map captured payment webhooks to recovery_outcomes
6. **Dashboard:** Query v_recovery_pipeline and v_recovery_metrics for UI display

---

## Schema Design Principles

✅ **Single Responsibility:** Each table has one purpose  
✅ **Denormalization:** JSON fields reduce joins for unstructured data  
✅ **Auditability:** Timestamps and reasoning chains preserved  
✅ **Performance:** Strategic indexes on hot paths  
✅ **Flexibility:** JSON fields support model/strategy evolution  
✅ **Feedback Loop:** new_payment_event_id closes the loop cleanly  
✅ **Production-Ready:** Foreign keys, constraints, proper datatypes
