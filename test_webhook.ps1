# Local webhook smoke test. It never stores credentials in source control.
param(
    [string]$WebhookSecret = $env:RAZORPAY_WEBHOOK_SECRET,
    [string]$DbUsername = $env:DB_USERNAME,
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$Database = "recoverflow"
)

if ([string]::IsNullOrWhiteSpace($WebhookSecret)) {
    throw "Set RAZORPAY_WEBHOOK_SECRET or pass -WebhookSecret before running this script."
}

$webhook = Get-Content "c:\Users\Asus\OneDrive\Desktop\razer_pay\backend\src\main\resources\sample-webhook-failed.json" -Raw
$secret = $WebhookSecret

# Generate HMAC-SHA256 signature
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
$hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($webhook))
$signature = ($hash | ForEach-Object { $_.ToString("x2") }) -join ""

Write-Host "=== STEP 1: Webhook Signature Verification ===" -ForegroundColor Cyan
Write-Host "Payload: sample-webhook-failed.json" -ForegroundColor White
Write-Host "Generated Signature: $signature" -ForegroundColor Green

Write-Host ""
Write-Host "=== STEP 2: Sending Webhook to Backend ===" -ForegroundColor Cyan

$response = $null
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/webhooks/payment" -Method POST -Headers @{"X-Razorpay-Signature"=$signature;"Content-Type"="application/json"} -Body $webhook -UseBasicParsing
    
    Write-Host "PASS: Webhook Received!" -ForegroundColor Green
    Write-Host "Status Code: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "Response: $($response.Content)" -ForegroundColor Yellow
} catch {
    Write-Host "FAIL: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== STEP 3: Checking Database for Payment ===" -ForegroundColor Cyan

if ($DbUsername -and $DbPassword) {
    $dbResult = mysql "-u$DbUsername" "-p$DbPassword" $Database -e "SELECT id, payment_id, status, error_code FROM payment_events WHERE status='FAILED' ORDER BY created_at DESC LIMIT 1;" 2>&1
    Write-Host "Database Result:" -ForegroundColor White
    Write-Host $dbResult -ForegroundColor Yellow
} else { Write-Host "Skipped: set DB_USERNAME and DB_PASSWORD to query MySQL." -ForegroundColor Yellow }

Write-Host ""
Write-Host "=== STEP 4: Checking ML Prediction ===" -ForegroundColor Cyan

if ($DbUsername -and $DbPassword) {
    $mlResult = mysql "-u$DbUsername" "-p$DbPassword" $Database -e "SELECT payment_event_id, failure_probability, root_cause_category FROM system_insights ORDER BY created_at DESC LIMIT 1;" 2>&1
    Write-Host "ML Prediction:" -ForegroundColor White
    Write-Host $mlResult -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== STEP 5: Checking AI Analysis ===" -ForegroundColor Cyan

if ($DbUsername -and $DbPassword) {
    $aiResult = mysql "-u$DbUsername" "-p$DbPassword" $Database -e "SELECT payment_event_id, root_cause_analysis, suggested_channel FROM ai_agent_analysis ORDER BY created_at DESC LIMIT 1;" 2>&1
    Write-Host "AI Analysis:" -ForegroundColor White
    Write-Host $aiResult -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== STEP 6: Testing Payment API ===" -ForegroundColor Cyan

$url = "http://localhost:8080/api/payments"
try {
    $apiPayments = Invoke-WebRequest -Uri $url -UseBasicParsing
    Write-Host "PASS: GET /api/payments" -ForegroundColor Green
    Write-Host "Response: $($apiPayments.Content.Substring(0, [Math]::Min(200, $apiPayments.Content.Length)))..." -ForegroundColor Yellow
} catch {
    Write-Host "FAIL: GET /api/payments - $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== STEP 7: Testing Dashboard API ===" -ForegroundColor Cyan

$dashUrl = "http://localhost:8080/api/dashboard/summary"
try {
    $dashboard = Invoke-WebRequest -Uri $dashUrl -UseBasicParsing
    Write-Host "PASS: GET /api/dashboard/summary" -ForegroundColor Green
    Write-Host "Response: $($dashboard.Content.Substring(0, [Math]::Min(200, $dashboard.Content.Length)))..." -ForegroundColor Yellow
} catch {
    Write-Host "FAIL: GET /api/dashboard/summary - $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== END-TO-END TEST COMPLETE ===" -ForegroundColor Cyan
