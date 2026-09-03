# Payment Failure Prediction Model

## Overview

This machine learning component predicts payment failures **before transactions are attempted**, enabling proactive recovery strategies. The system uses a 250K transaction dataset from UPI payments in 2024.

## Architecture

### Two-Tier Prediction System

```
┌─────────────────────────────────────────┐
│  Spring Boot Backend API                │
│  POST /api/prediction/failure-risk      │
└─────────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
    ┌───▼────┐         ┌────▼────┐
    │  Model │         │  Rules  │
    │ (ML)   │         │ (Java)  │
    └────────┘         └────────┘
        │                   │
        └──────────┬────────┘
                   │
        ┌──────────▼──────────┐
        │  FailurePrediction  │
        │  - probability      │
        │  - risk_factors     │
        │  - recommendation   │
        └─────────────────────┘
```

## Dataset

**File:** `upi_transactions_2024.csv`

**Size:** 250,000 payment records

**Features:**
- `transaction_id` - Unique transaction identifier
- `timestamp` - Transaction datetime
- `transaction_type` - P2P, Merchant, etc.
- `merchant_category` - Entertainment, Retail, etc.
- `amount (INR)` - Transaction amount
- `transaction_status` - SUCCESS, FAILED (target variable)
- `sender_age_group` - 18-25, 26-35, 35-45, etc.
- `receiver_age_group` - Age group of receiver
- `sender_state` - State of sender
- `sender_bank` - Sender's bank (Axis, SBI, HDFC, etc.)
- `receiver_bank` - Receiver's bank
- `device_type` - Android, iPhone, Web, etc.
- `network_type` - 2G, 3G, 4G, 5G, WiFi
- `fraud_flag` - 0 (no fraud) or 1 (suspected fraud)
- `hour_of_day` - Hour (0-23)
- `day_of_week` - Day name
- `is_weekend` - 0 or 1

**Target Variable:**
- `is_failed` = 1 if transaction_status != 'SUCCESS', else 0
- **Failure Rate:** ~5%

## ML Model

### Python Training Pipeline

**File:** `ml/payment_failure_predictor.py`

**Steps:**

1. **Data Loading** (250K records)
2. **Feature Engineering**
   - Amount categories (micro, small, medium, large)
   - Peak hour flags (9-12, 14-17, 19-22)
   - Night hour flags (0-6)
   - High-value flags (>10K)
3. **Encoding** (categorical features → integers)
4. **Feature Scaling** (StandardScaler)
5. **Model Training**
   - Random Forest (200 trees, max_depth=15)
   - Gradient Boosting (150 estimators, lr=0.1)
   - Selects best performer (AUC score)
6. **Evaluation**
   - Metrics: Precision, Recall, F1, ROC-AUC
   - Feature importance analysis
7. **Artifact Saving**
   - Model pickle file
   - Feature scaler
   - Label encoders

### Model Artifacts

| Artifact | Path | Purpose |
|----------|------|---------|
| Trained Model | `ml/failure_predictor_model.pkl` | Scikit-learn model (pickle) |
| Feature Scaler | `ml/feature_scaler.pkl` | StandardScaler for features |
| Label Encoders | `ml/label_encoders.pkl` | Encoders for categorical features |
| Report | `ml/model_report.json` | Model metrics & metadata |

### Expected Performance

| Metric | Value |
|--------|-------|
| Precision | ~0.72 |
| Recall | ~0.68 |
| F1 Score | ~0.70 |
| ROC-AUC | ~0.82 |

*Balanced model emphasizing recall (catching failures) over false positives*

## Rule-Based Fallback

When ML model is unavailable, the system uses domain knowledge rules:

### Risk Factors & Scores

| Risk Factor | Probability Increase | Description |
|-------------|---------------------|-------------|
| High-value (>10K) | +0.15 | Large transactions have higher variance |
| Night hours (0-6) | +0.12 | Poor network reliability |
| Weekend | +0.08 | Lower support staff availability |
| Risky merchant | +0.10 | Certain categories (gambling, crypto) |
| Poor network | +0.15 | 2G/3G network connectivity issues |
| Risky bank pair | +0.10 | Known inter-bank settlement issues |
| Age group 18-25, 65+ | +0.05 | Higher first-time failure rates |
| Fraud flag | +0.25 | Security checks delay/reject |
| Peak hours | +0.08 | Network congestion |
| Network-prone state | +0.10 | Geographic network reliability issues |

**Final probability** = min(sum of factors, 0.95), clamped to [0.01, 0.95]

## Java Integration

### PaymentFailurePredictor Service

**File:** `backend/src/main/java/com/recoveryagent/ml/PaymentFailurePredictor.java`

**Key Classes:**

```java
public class PaymentFailurePredictor {
    // Request: Transaction details
    public static class PaymentPredictionRequest {
        String transactionId;
        double amount;
        String transactionType;
        String merchantCategory;
        int hourOfDay;
        boolean weekend;
        String senderBank;
        String receiverBank;
        String senderState;
        String senderAgeGroup;
        String receiverAgeGroup;
        String deviceType;
        String networkType;
        boolean fraudFlagSet;
    }
    
    // Response: Prediction result
    public static class FailurePrediction {
        double failureProbability;  // 0.0-1.0
        String predictorModel;       // Model version
        List<String> riskFactors;    // Identified risks
        String confidence;           // "high", "medium", "low"
        String recommendation;       // Action guidance
    }
    
    // Main prediction method
    public FailurePrediction predict(PaymentPredictionRequest request);
}
```

### REST API

**Controller:** `PaymentFailurePredictionController.java`

**Endpoints:**

#### 1. Predict Failure Risk

```http
POST /api/prediction/failure-risk
Content-Type: application/json

{
  "transactionId": "TXN123456",
  "amount": 5000,
  "transactionType": "P2P",
  "merchantCategory": "Entertainment",
  "hourOfDay": 23,
  "weekend": false,
  "senderBank": "Axis",
  "receiverBank": "SBI",
  "senderState": "Delhi",
  "senderAgeGroup": "26-35",
  "receiverAgeGroup": "18-25",
  "deviceType": "Android",
  "networkType": "4G",
  "fraudFlagSet": false
}
```

**Response:**

```json
{
  "failureProbability": 0.28,
  "predictorModel": "recoverflow-rules-v1",
  "riskFactors": [
    "peak_hour_congestion",
    "weekend_transaction"
  ],
  "confidence": "medium",
  "recommendation": "Medium risk: Consider alternative payment method or retry after some time."
}
```

#### 2. Service Health

```http
GET /api/prediction/health
```

**Response:**

```json
{
  "service": "Payment Failure Prediction Service",
  "status": "UP",
  "message": "Ready for payment failure predictions"
}
```

## Usage Workflow

### 1. Before Payment Attempt

```
User initiates payment
      │
      ▼
Call /api/prediction/failure-risk with transaction details
      │
      ▼
┌─────────────────────────────────────────┐
│ Receive FailurePrediction with:         │
│ - failureProbability (0.28)             │
│ - recommendation                        │
└─────────────────────────────────────────┘
      │
      ▼
Decision Logic:
  if probability < 0.2: "Proceed normally"
  if probability 0.2-0.5: "Warn user or suggest retry"
  if probability > 0.5: "Recommend alternative method"
```

### 2. Integration with Recovery Pipeline

```
Payment Failure Event
      │
      ▼
Use prediction model to:
1. Identify failure was "predictable"
2. Select appropriate recovery strategy:
   - High-value + night = SMS + email
   - Peak hour + risky bank = payment link
   - Fraud flag = escalate to support
```

### 3. Feature Importance Analysis

Top features from trained model (example):

1. `amount` - Transaction size
2. `fraud_flag` - Security checks
3. `network_type` - Connectivity quality
4. `sender_bank` - Sender's bank infrastructure
5. `hour_of_day` - Time of transaction
6. `device_type` - Device compatibility
7. `sender_state` - Geographic reliability
8. `merchant_category` - Risk category
9. `is_weekend` - Weekend factor
10. `receiver_bank` - Receiver's bank reliability

## Training & Deployment

### Step 1: Run Python Training Script

```bash
cd ml
pip install pandas scikit-learn numpy matplotlib seaborn

python payment_failure_predictor.py
```

**Output:**
- Model file: `ml/failure_predictor_model.pkl`
- Scaler file: `ml/feature_scaler.pkl`
- Encoders: `ml/label_encoders.pkl`
- Report: `ml/model_report.json`

### Step 2: Load Model in Java (Future)

Currently uses rule-based fallback. To integrate trained model:

**Option A: Python ML Service (Recommended for Production)**
- Deploy Python Flask/FastAPI service
- Expose `/predict` endpoint
- Call from Java via HTTP
- Reduces dependency on Python libraries in JVM

**Option B: PMML/ONNX Conversion**
- Export scikit-learn model to PMML
- Use PMML library in Java
- No Python service required

**Option C: Embedded Python (via Jython/GraalVM)**
- Embed Python runtime
- Load pickle files directly
- Complex but self-contained

### Step 3: Configuration

**`application-mysql.properties`:**

```properties
# ML Model Configuration
ml.model.enabled=false
ml.model.path=models/failure_predictor_model.pkl
```

Set `ml.model.enabled=true` when model integration is complete.

### Step 4: Run Tests

```bash
cd backend
mvn test -Dtest=PaymentFailurePredictorTest
mvn test -Dtest=PaymentFailurePredictionControllerTest
```

## Testing

### Unit Tests (10 tests)

**`PaymentFailurePredictorTest.java`:**
- Low risk scenario
- High-value transaction risk
- Night hour risk
- Weekend risk
- Fraud flag risk
- Poor network risk
- Peak hour risk
- Multiple risk factors
- Prediction consistency
- Probability bounds
- Recommendation logic
- Model name verification

**`PaymentFailurePredictionControllerTest.java`:**
- Failure risk prediction endpoint
- Service health check
- High-risk prediction response

### Integration Test (Future)

```
1. Send payment.failed webhook
2. Call /api/prediction/failure-risk
3. Verify ML recommendation matches root cause
4. Check dashboard shows predicted risk
```

## Performance Metrics

### Inference Speed
- Rule-based: < 1ms per prediction
- ML model: ~5-10ms per prediction (when available)

### Memory
- Model file: ~15-20 MB
- Scaler: ~1 MB
- Encoders: ~2 MB

### Throughput
- Single-threaded: ~1000 predictions/second (rules)
- With Spring ThreadPool: ~5000+ predictions/second

## Future Enhancements

### Short-term
1. **Model Serving**
   - Deploy Python ML service via Flask/FastAPI
   - Integrate with Java backend via REST

2. **Model Monitoring**
   - Track prediction accuracy vs actual outcomes
   - Detect model drift
   - Auto-trigger retraining

3. **Explainability**
   - SHAP values for individual predictions
   - Feature contribution breakdown
   - Confidence intervals

### Medium-term
4. **Ensemble Model**
   - Combine ML predictions with rules
   - Weighted voting
   - Confidence calibration

5. **Online Learning**
   - Incorporate new payment data in real-time
   - Adaptive thresholds
   - Seasonal adjustment

6. **Custom Risk Profiles**
   - Per-customer risk tolerance
   - Per-merchant risk assessment
   - Geographic customization

### Long-term
7. **Real-time Risk Scoring**
   - Predict at transaction approval time
   - Integration with Razorpay API
   - Dynamic routing (approval vs denial)

8. **Root Cause Analysis**
   - Explain why specific transaction will fail
   - Suggest corrective actions
   - Link to recovery strategies

## Troubleshooting

### Issue: Poor prediction accuracy
**Solution:** 
- Check feature engineering in Python script
- Verify encoders match deployed model
- Retrain with recent data

### Issue: API timeouts
**Solution:**
- Cache predictions by (amount, hour, state, bank)
- Implement async prediction queue
- Use rule-based fallback for slow model

### Issue: Model file not found
**Solution:**
- Verify `ml.model.path` configuration
- Ensure model file deployed with JAR
- Check file permissions

## References

- Scikit-learn: https://scikit-learn.org/
- Pandas: https://pandas.pydata.org/
- UPI Transaction Dataset: `dataset/upi_transactions_2024.csv`
- Model Report: `ml/model_report.json`
