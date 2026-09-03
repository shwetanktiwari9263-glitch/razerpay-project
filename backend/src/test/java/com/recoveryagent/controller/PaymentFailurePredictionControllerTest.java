package com.recoveryagent.controller;

import com.recoveryagent.ml.PaymentFailurePredictor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit test for PaymentFailurePredictionController logic No Spring
 * context needed - tests pure prediction logic
 */
class PaymentFailurePredictionControllerTest {

    @Test
    void testLowRiskPredictionResponse() {
        PaymentFailurePredictor predictor = new PaymentFailurePredictor();

        PaymentFailurePredictor.PaymentPredictionRequest request
                = new PaymentFailurePredictor.PaymentPredictionRequest(
                        "TXN123456", 5000.0, "P2P", "Entertainment", 14, false,
                        "Axis", "SBI", "Delhi", "26-35", "18-25", "Android", "4G", false
                );

        PaymentFailurePredictor.FailurePrediction prediction = predictor.predict(request);

        assertNotNull(prediction);
        assertNotNull(prediction.getFailureProbability());
        assertNotNull(prediction.getPredictorModel());
        assertNotNull(prediction.getRiskFactors());
        assertNotNull(prediction.getConfidence());
        assertNotNull(prediction.getSuccessProbability());
        assertNotNull(prediction.getRiskLevel());
        assertTrue(prediction.getFailureProbability() >= 0.0);
        assertTrue(prediction.getFailureProbability() <= 1.0);
    }

    @Test
    void testHighRiskPredictionResponse() {
        PaymentFailurePredictor predictor = new PaymentFailurePredictor();

        PaymentFailurePredictor.PaymentPredictionRequest highRiskRequest
                = new PaymentFailurePredictor.PaymentPredictionRequest(
                        "TXN999999", 50000.0, "Merchant", "Gambling", 3, true,
                        "Yes", "PNB", "Assam", "18-25", "35-45", "Android", "2G", true
                );

        PaymentFailurePredictor.FailurePrediction prediction = predictor.predict(highRiskRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getFailureProbability() > 0.4);
        assertEquals("low", prediction.getConfidence());
        assertTrue(prediction.getRiskFactors().size() >= 3);
        assertEquals("HIGH", prediction.getRiskLevel());
    }

    @Test
    void testResponseStructure() {
        PaymentFailurePredictor predictor = new PaymentFailurePredictor();

        PaymentFailurePredictor.PaymentPredictionRequest request
                = new PaymentFailurePredictor.PaymentPredictionRequest(
                        "TXN123", 2000.0, "P2P", "Retail", 10, false,
                        "SBI", "HDFC", "Mumbai", "26-35", "26-35", "iPhone", "5G", false
                );

        PaymentFailurePredictor.FailurePrediction prediction = predictor.predict(request);

        // Verify all required fields are present
        assertNotNull(prediction.getFailureProbability(), "failureProbability should not be null");
        assertNotNull(prediction.getPredictorModel(), "predictorModel should not be null");
        assertNotNull(prediction.getRiskFactors(), "riskFactors should not be null");
        assertNotNull(prediction.getConfidence(), "confidence should not be null");
        assertNotNull(prediction.getSuccessProbability(), "successProbability should not be null");
        assertNotNull(prediction.getRiskLevel(), "riskLevel should not be null");

        // Verify confidence values are valid
        assertTrue(
                prediction.getConfidence().equals("high")
                || prediction.getConfidence().equals("medium")
                || prediction.getConfidence().equals("low"),
                "confidence must be one of: high, medium, low"
        );
    }
}
