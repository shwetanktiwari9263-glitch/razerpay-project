package com.recoveryagent.controller;

import com.recoveryagent.ml.PaymentFailurePredictor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * Payment Failure Prediction API Provides endpoints to predict payment failure
 * probability before transaction Enables proactive recovery strategy selection
 */
@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
@Profile("mysql")
public class PaymentFailurePredictionController {

    private final PaymentFailurePredictor failurePredictor;

    /**
     * Predict payment failure probability
     *
     * @param request Payment transaction details
     * @return Failure prediction with probability and risk factors
     *
     * Example request: { "transactionId": "TXN123456", "amount": 5000,
     * "transactionType": "P2P", "merchantCategory": "Entertainment",
     * "hourOfDay": 23, "weekend": false, "senderBank": "Axis", "receiverBank":
     * "SBI", "senderState": "Delhi", "senderAgeGroup": "26-35",
     * "receiverAgeGroup": "18-25", "deviceType": "Android", "networkType":
     * "4G", "fraudFlagSet": false }
     *
     * Example response: { "failureProbability": 0.28, "predictorModel":
     * "recoverflow-rules-v1", "riskFactors": ["high_value_transaction",
     * "peak_hour_congestion"], "confidence": "medium", "recommendation":
     * "Medium risk: Consider alternative payment method or retry after some
     * time." }
     */
    @PostMapping("/failure-risk")
    public PaymentFailurePredictor.FailurePrediction predictFailureRisk(
            @RequestBody PaymentFailurePredictor.PaymentPredictionRequest request) {

        return failurePredictor.predict(request);
    }

    /**
     * Quick health check for prediction service
     *
     * @return Service status
     */
    @GetMapping("/health")
    public PredictionServiceStatus getStatus() {
        return new PredictionServiceStatus(
                "Payment Failure Prediction Service",
                "UP",
                "Ready for payment failure predictions"
        );
    }

    /**
     * Service status response
     */
    public record PredictionServiceStatus(
            String service,
            String status,
            String message
            ) {

    }
}
