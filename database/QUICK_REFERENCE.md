# RecoverFlow: Quick Reference Guide

## 5-Table Data Model Summary

| Table | Purpose | Foreign Keys | Key Status Fields |
|-------|---------|--------------|-------------------|
| **payment_events** | Raw Razorpay webhooks | None (root table) | status: failed/captured/authorized/refunded |
| **system_insights** | ML analysis of failure | → payment_events | degradation_status: healthy/degraded/critical |
| **ai_agent_analysis** | AI recovery strategy | → payment_events, → system_insights | suggested_channel: retry/payment_link/notification/none |
| **recovery_actions** | Rules engine execution | → ai_agent_analysis, → payment_events | execution_status: triggered/pending/executed/failed |
| **recovery_outcomes** | Feedback loop & success | → recovery_actions, → payment_events (x2) | outcome_status: success/failed/pending/timeout |

---

## Feedback Loop Flow

```
PAYMENT FAILS (payment.failed webhook)
    ↓ INSERT into payment_events (status='failed')
    ↓ 
ANALYZE FAILURE (Mock ML Model)
    ↓ INSERT into system_insights (failure_probability, root_cause_category)
    ↓
SUGGEST RECOVERY (AI Agent Decision)
    ↓ INSERT into ai_agent_analysis (suggested_channel, confidence_score)
    ↓
EXECUTE ACTION (Rules Engine)
    ↓ INSERT into recovery_actions (action_type='payment_link' or 'retry', execution_status='triggered')
    ↓
WAIT FOR RECOVERY
    ↓
PAYMENT SUCCEEDS (payment.captured webhook)
    ↓ INSERT into payment_events (status='captured') [NEW RECORD]
    ↓
RECORD OUTCOME (Link captured payment back to recovery attempt)
    ↓ INSERT into recovery_outcomes (
       recovery_success=TRUE,
       new_payment_event_id=[captured_payment_id],
       time_to_recovery=[seconds elapsed]
    )
    ↓
COMPLETE FEEDBACK LOOP ✓
```

---

## Key Indexes for Query Performance

| Table | Index | Purpose |
|-------|-------|---------|
| payment_events | (status, created_at) | Fast query: "Show me failed payments from today" |
| system_insights | (degradation_status) | Filter by system health |
| ai_agent_analysis | (suggested_channel) | Group recovery by action type |
| recovery_actions | (execution_status) | Track pending actions |
| recovery_outcomes | (recovery_success) | Dashboard: success rate calculation |

---

## Foreign Key Cascade Rules

**ON DELETE CASCADE:**
- payment_events → all downstream tables
- ai_agent_analysis → recovery_actions
- recovery_actions → recovery_outcomes

**ON DELETE SET NULL:**
- recovery_outcomes.new_payment_event_id (preserve outcome record if recovered payment deleted)

---

## SQL Queries for Common Use Cases

### 1. Find Failed Payments Not Yet Recovered
```sql
SELECT pe.id, pe.payment_id, pe.amount, pe.error_description, pe.created_at
FROM payment_events pe
LEFT JOIN recovery_outcomes ro ON EXISTS (
    SELECT 1 FROM recovery_actions ra 
    WHERE ra.payment_event_id = pe.id 
    AND ra.id = ro.recovery_action_id 
    AND ro.recovery_success = TRUE
)
WHERE pe.status = 'failed' 
  AND ro.id IS NULL
  AND pe.created_at > DATE_SUB(NOW(), INTERVAL 7 DAY);
```

### 2. Recovery Success Rate (Last 24 Hours)
```sql
SELECT 
    COUNT(*) AS total_attempts,
    SUM(CASE WHEN recovery_success = TRUE THEN 1 ELSE 0 END) AS successful,
    ROUND(100.0 * SUM(CASE WHEN recovery_success = TRUE THEN 1 ELSE 0 END) / COUNT(*), 2) AS success_rate_pct
FROM recovery_outcomes
WHERE created_at > DATE_SUB(NOW(), INTERVAL 1 DAY);
```

### 3. Average Recovery Time by Channel
```sql
SELECT 
    aa.suggested_channel,
    COUNT(*) AS attempts,
    AVG(ro.time_to_recovery) AS avg_seconds,
    MAX(ro.time_to_recovery) AS max_seconds
FROM recovery_actions ra
JOIN ai_agent_analysis aa ON ra.ai_agent_analysis_id = aa.id
JOIN recovery_outcomes ro ON ra.id = ro.recovery_action_id
WHERE ro.recovery_success = TRUE
GROUP BY aa.suggested_channel;
```

### 4. Root Cause Analysis: Most Common Failures
```sql
SELECT 
    si.root_cause_category,
    COUNT(*) AS count,
    SUM(CASE WHEN ro.recovery_success = TRUE THEN 1 ELSE 0 END) AS recovered_count,
    ROUND(100.0 * SUM(CASE WHEN ro.recovery_success = TRUE THEN 1 ELSE 0 END) / COUNT(*), 2) AS recovery_rate_pct
FROM payment_events pe
JOIN system_insights si ON pe.id = si.payment_event_id
LEFT JOIN ai_agent_analysis aa ON pe.id = aa.payment_event_id
LEFT JOIN recovery_actions ra ON aa.id = ra.ai_agent_analysis_id
LEFT JOIN recovery_outcomes ro ON ra.id = ro.recovery_action_id
WHERE pe.status = 'failed'
GROUP BY si.root_cause_category
ORDER BY count DESC;
```

### 5. Cohort Analysis: Payment Link vs Auto-Retry Recovery Rates
```sql
SELECT 
    ra.action_type,
    COUNT(*) AS total_actions,
    SUM(CASE WHEN ro.recovery_success = TRUE THEN 1 ELSE 0 END) AS successes,
    AVG(ro.time_to_recovery) AS avg_recovery_time_sec,
    SUM(ro.recovered_amount) AS total_recovered_amount
FROM recovery_actions ra
LEFT JOIN recovery_outcomes ro ON ra.id = ro.recovery_action_id
GROUP BY ra.action_type;
```

---

## JSON Field Structures (Examples)

### payment_events.raw_payload
```json
{
  "id": "evt_123abc",
  "event": "payment.failed",
  "created_at": 1629802800,
  "payload": {
    "payment": {
      "id": "pay_456def",
      "entity": "payment",
      "amount": 50000,
      "currency": "INR",
      "status": "failed",
      "method": "card",
      "card": { "entity": "card", "network": "Visa", "issuer": "HDFC" },
      "error_code": "BAD_REQUEST_ERROR",
      "error_description": "Card declined"
    }
  }
}
```

### system_insights.additional_metadata
```json
{
  "retry_recommended": true,
  "wait_duration_seconds": 3600,
  "affected_gateway_status": "degraded",
  "similar_failures_count": 156,
  "estimated_fix_time_minutes": 45
}
```

### recovery_actions.action_metadata
```json
{
  "retry_count": 3,
  "retry_delay_seconds": 1800,
  "backoff_multiplier": 1.5,
  "link_expiry_timestamp": 1629889200,
  "sms_template_id": "tpl_payment_recovery_v2",
  "customer_note": "Your payment failed. Click here to retry."
}
```

### recovery_outcomes.feedback_notes
```json
{
  "customer_feedback": "Customer re-attempted and payment succeeded",
  "manual_intervention_required": false,
  "follow_up_recommended": false,
  "reason_for_original_failure": "Insufficient balance at time of retry (resolved after 1 hour)",
  "agent_notes": "Monitor this customer for frequent failures"
}
```

---

## Setup Instructions (Copy-Paste Ready)

### 1. Create Database
```bash
mysql -u root -p < database/schema.sql
```

### 2. Set Environment Variables
```bash
export DB_URL="jdbc:mysql://localhost:3306/recoverflow"
export DB_USERNAME="root"
export DB_PASSWORD="your_password"
```

### 3. Run Backend with MySQL Profile
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### 4. Verify Schema Created
```sql
USE recoverflow;
SHOW TABLES;
DESCRIBE payment_events;
```

---

## Dashboard Metrics (Frontend Integration Points)

### Metric 1: Recovery Success Rate
```
Query: v_recovery_metrics (view)
Display: Green gauge showing success_rate_pct
Update Frequency: Real-time / 5-min refresh
```

### Metric 2: Revenue Recovered (Last 30 Days)
```
Query: SUM(recovered_amount) FROM recovery_outcomes WHERE recovered = TRUE AND created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
Display: Large number with currency symbol
Example: ₹15,67,420
```

### Metric 3: Top Failure Reasons
```
Query: root_cause_category with count, grouped by recovery_rate
Display: Bar chart (category vs recovery rate %)
Insight: Which failure types have best recovery potential
```

### Metric 4: Recovery Speed (Avg Time)
```
Query: AVG(time_to_recovery) by suggested_channel
Display: Horizontal bar showing payment_link vs retry speed
Insight: Which recovery method is fastest
```

### Metric 5: Live Recovery Pipeline
```
Query: v_recovery_pipeline with status filters
Display: Table showing current failures and recovery attempt status
Insight: Real-time overview of active recoveries
```

---

## Troubleshooting Guide

| Problem | Cause | Solution |
|---------|-------|----------|
| Foreign key constraint error | Inserting into child table before parent | Ensure payment_events exists before system_insights |
| Duplicate entry on webhook retry | Not checking event_id uniqueness | Always query by event_id before inserting |
| Recovery loop not closing | payment.captured webhook not triggered | Check Razorpay webhook endpoint is accessible |
| Null new_payment_event_id | Captured payment not inserted yet | Ensure webhook handler processes all events in order |
| Slow dashboard queries | No indexes on filter columns | Review index list and run ANALYZE TABLE |
| High DB storage | JSON payloads taking space | Consider archiving old events after 90 days |

---

## Scaling Considerations (Post-Buildathon)

1. **Partitioning:** Partition recovery_outcomes by created_at (monthly)
2. **Archive Strategy:** Move events older than 6 months to separate table
3. **Caching Layer:** Cache v_recovery_metrics in Redis (5-min TTL)
4. **Read Replicas:** Add read replica for analytics queries
5. **Async Processing:** Use message queue for recovery action execution
6. **Batch Insights:** Run system_insights generation as batch job vs real-time

---

## Schema Version & Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-28 | Initial schema for Razorpay Buildathon |
| — | — | — |
| Future | TBD | Add event_audit table for compliance |

---

## File Manifest

```
database/
├── schema.sql                    ← Copy-paste into MySQL
├── SCHEMA_GUIDE.md              ← Detailed table explanations
├── ENTITY_TEMPLATES.md          ← Spring Boot JPA entity code templates
├── IMPLEMENTATION_ROADMAP.md    ← Task-by-task buildathon plan
└── QUICK_REFERENCE.md           ← This file
```

---

## Contact / Support

For schema questions during buildathon:
- Check SCHEMA_GUIDE.md for table definitions
- Check IMPLEMENTATION_ROADMAP.md for code templates
- Review SQL queries section for dashboard queries
- Refer to JSON structures for field format examples
