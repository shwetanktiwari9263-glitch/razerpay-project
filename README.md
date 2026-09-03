# Razorpay AI Payment Recovery

Local, full-stack project for receiving Razorpay payment webhooks, analysing failed payments with an ML risk score and Groq/rule-based recovery advisor, tracking recovery attempts in MySQL, and displaying the result in a React dashboard.

## What is included

- Spring Boot backend (`backend/`) with HMAC-verified Razorpay webhook handling.
- MySQL persistence for payment events, ML insights, AI analyses, recovery actions, and recovery outcomes.
- FastAPI model service (`ml/`) serving `failure_predictor.joblib`.
- React/Vite dashboard (`frontend/`) for payments, failure analytics, recovery advice, and pre-payment risk estimates.
- Razorpay Payment Link integration. When Razorpay credentials are absent it deliberately records a pending mock link rather than claiming a real action was sent.
- Groq recovery recommendations with a deterministic rule-based fallback when no API key is configured.

## Prerequisites

- Java 17+ and Maven
- Node.js 18+
- Python 3.10+
- MySQL 8+

## Local configuration

1. Copy `.env.example` to `.env` and fill in local values. Do not commit `.env`.
2. Create the `recoverflow` database. The backend initialises tables from `backend/src/main/resources/schema-mysql.sql` when started with the `mysql` profile.
3. Set the variables in your current PowerShell session. Spring Boot does not load `.env` automatically:

```powershell
$env:SPRING_PROFILES_ACTIVE = "mysql"
$env:DB_URL = "jdbc:mysql://localhost:3306/recoverflow"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-local-password"
$env:RAZORPAY_WEBHOOK_SECRET = "your-local-test-webhook-secret"
$env:RAZORPAY_KEY_ID = "rzp_test_..."       # optional; required for real payment links
$env:RAZORPAY_KEY_SECRET = "..."            # optional; required for real payment links
$env:GROQ_API_KEY = "..."                    # optional; rules are used if omitted
$env:ML_SERVICE_ENABLED = "true"
$env:ML_SERVICE_URL = "http://localhost:8000"
```

## Run locally

Use three terminals.

```powershell
# Terminal 1: ML service
cd ml
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app:app --reload --port 8000
```

```powershell
# Terminal 2: backend (set the environment variables above in this terminal)
cd backend
mvn spring-boot:run
```

```powershell
# Terminal 3: frontend
cd frontend
Copy-Item .env.example .env
npm install
npm run dev
```

Open `http://localhost:5173`. Health checks: `http://localhost:8000/health`, `http://localhost:8080/api/health`, and `http://localhost:8080/api/prediction/health`.

## Webhook flow

`POST /api/webhooks/payment` verifies `X-Razorpay-Signature` against the exact raw request body. A `payment.failed` event is saved, scored, analysed, and assigned a recovery action. A unique matching `payment.captured` event closes the pending recovery outcome.

For a local smoke test, first start the backend and then run:

```powershell
$env:RAZORPAY_WEBHOOK_SECRET = "your-local-test-webhook-secret"
.\test_webhook.ps1
```

The script reads secrets from the environment and never contains credentials. Configure the same webhook secret in Razorpay Test Mode before testing live webhooks.

## Important local behaviour

- Real Razorpay Payment Links require `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`. Without them, the action remains pending and is explicitly marked as mock data.
- Retry, notification, and customer-support recommendations are tracked as pending manual/channel actions; this project does not claim to send an SMS, email, or retry a customer payment without an integrated provider.
- The ML service is functional and the training script produces the artifact it serves. Its reported model quality must be reviewed before relying on predictions for business decisions.

## Verification

```powershell
cd backend; mvn test
cd ..\frontend; npm run build
```

The MySQL integration test is opt-in because it requires a local database:

```powershell
$env:RUN_MYSQL_IT = "true"
cd backend
mvn test -Dtest=RecoveryFlowMySqlIntegrationTest
```

## Repository hygiene

Never commit `.env`, database passwords, Razorpay secrets, or Groq keys. `.gitignore` excludes local environment files, virtual environments, Node dependencies, and build outputs.
