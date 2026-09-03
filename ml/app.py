"""HTTP service for the trained payment-failure model.

The service deliberately returns prediction data only. Recovery explanations
and actions remain the responsibility of the Java AI recovery layer.
"""
from datetime import datetime
from pathlib import Path
from typing import Literal

import joblib
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

MODEL_PATH = Path(__file__).with_name("failure_predictor.joblib")
artifact = joblib.load(MODEL_PATH)
model = artifact["model"]

app = FastAPI(title="Payment Failure ML Service", version="1.0.0")


class PredictionRequest(BaseModel):
    transactionId: str = Field(min_length=1)
    amount: float = Field(ge=0)
    transactionType: str
    merchantCategory: str
    hourOfDay: int = Field(ge=0, le=23)
    weekend: bool
    senderBank: str
    receiverBank: str
    senderState: str
    senderAgeGroup: str
    receiverAgeGroup: str
    deviceType: str
    networkType: str
    fraudFlagSet: bool


def risk_level(failure_probability: float) -> Literal["LOW", "MEDIUM", "HIGH"]:
    if failure_probability >= 0.70:
        return "HIGH"
    if failure_probability >= 0.40:
        return "MEDIUM"
    return "LOW"


def feature_frame(request: PredictionRequest) -> pd.DataFrame:
    now = datetime.now()
    return pd.DataFrame([{
        "transaction_type": request.transactionType,
        "merchant_category": request.merchantCategory,
        "sender_age_group": request.senderAgeGroup,
        "receiver_age_group": request.receiverAgeGroup,
        "sender_state": request.senderState,
        "sender_bank": request.senderBank,
        "receiver_bank": request.receiverBank,
        "device_type": request.deviceType,
        "network_type": request.networkType,
        "day_of_week": now.strftime("%A"),
        "amount_inr": request.amount,
        "hour_of_day": request.hourOfDay,
        "fraud_flag": int(request.fraudFlagSet),
        "is_weekend": int(request.weekend),
        "is_peak_hour": int(request.hourOfDay in {9, 10, 11, 12, 14, 15, 16, 17, 19, 20, 21, 22}),
        "is_night": int(request.hourOfDay in {0, 1, 2, 3, 4, 5, 6}),
        "is_high_value": int(request.amount > 10000),
    }])


@app.get("/health")
def health():
    return {"status": "UP", "model": artifact["model_name"], "artifact": MODEL_PATH.name}


@app.post("/predict")
def predict(request: PredictionRequest):
    try:
        failure_probability = round(float(model.predict_proba(feature_frame(request))[:, 1][0]), 4)
        return {
            "successProbability": round(1 - failure_probability, 4),
            "failureProbability": failure_probability,
            "riskLevel": risk_level(failure_probability),
            "predictorModel": f"sklearn-{artifact['model_name']}",
            "riskFactors": [],
            # The classifier returns a probability, not a calibrated confidence
            # interval. Keep the API's confidence contract explicit and stable.
            "confidence": "medium",
        }
    except Exception as error:
        raise HTTPException(status_code=500, detail="ML prediction could not be completed") from error
