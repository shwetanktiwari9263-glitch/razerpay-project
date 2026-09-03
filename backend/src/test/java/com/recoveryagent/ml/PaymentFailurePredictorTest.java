package com.recoveryagent.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentFailurePredictor Tests rule-based prediction logic - No
 * Spring context needed
 */
class PaymentFailurePredictorTest {

    private PaymentFailurePredictor failurePredictor;
    private PaymentFailurePredictor.PaymentPredictionRequest baseRequest;

    @BeforeEach
    void setUp() {
        failurePredictor = new PaymentFailurePredictor();
        baseRequest = new PaymentFailurePredictor.PaymentPredictionRequest(
                "TXN123456",
                5000.0,
                "P2P",
                "Entertainment",
                14,
                false,
                "Axis",
                "SBI",
                "Delhi",
                "26-35",
                "18-25",
                "Android",
                "4G",
                false
        );
    }

    @Test
    void testLowRiskPrediction() {
        PaymentFailurePredictor.PaymentPredictionRequest lowRiskRequest
                = new PaymentFailurePredictor.PaymentPredictionRequest(
                        "TXN001", 1000.0, "P2P", "Retail", 10, false,
                        "SBI", "HDFC", "Mumbai", "26-35", "26-35", "iPhone", "5G", false
                );

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(lowRiskRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getFailureProbability() < 0.3);
        assertEquals("high", prediction.getConfidence());
        assertEquals("LOW", prediction.getRiskLevel());
        assertEquals(1.0, prediction.getSuccessProbability() + prediction.getFailureProbability(), 0.0001);
    }

    @Test
    void testHighValueTransactionRisk() {
        PaymentFailurePredictor.PaymentPredictionRequest highValueRequest = baseRequest;
        highValueRequest.setAmount(15000.0);

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(highValueRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getFailureProbability() > 0.15);
        assertTrue(prediction.getRiskFactors().contains("high_value_transaction"));
    }

    @Test
    void testNightHourRisk() {
        PaymentFailurePredictor.PaymentPredictionRequest nightRequest = baseRequest;
        nightRequest.setHourOfDay(3);

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(nightRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getRiskFactors().contains("night_transaction"));
        assertTrue(prediction.getFailureProbability() > 0.1);
    }

    @Test
    void testWeekendRisk() {
        PaymentFailurePredictor.PaymentPredictionRequest weekendRequest = baseRequest;
        weekendRequest.setWeekend(true);

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(weekendRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getRiskFactors().contains("weekend_transaction"));
    }

    @Test
    void testFraudFlagRisk() {
        PaymentFailurePredictor.PaymentPredictionRequest fraudRequest = baseRequest;
        fraudRequest.setFraudFlagSet(true);

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(fraudRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getFailureProbability() > 0.25);
        assertTrue(prediction.getRiskFactors().contains("fraud_suspicion"));
        // Fraud with other factors can push it to "low" confidence
        assertTrue(
            prediction.getConfidence().equals("low") || prediction.getConfidence().equals("medium"),
            "confidence should be low or medium"
        );
    }

    @Test
    void testPoorNetworkRisk() {
        PaymentFailurePredictor.PaymentPredictionRequest poorNetworkRequest = baseRequest;
        poorNetworkRequest.setNetworkType("2G");

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(poorNetworkRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getRiskFactors().contains("poor_network_quality"));
        assertTrue(prediction.getFailureProbability() > 0.1);
    }

    @Test
    void testPeakHourRisk() {
        PaymentFailurePredictor.PaymentPredictionRequest peakHourRequest = baseRequest;
        peakHourRequest.setHourOfDay(20);

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(peakHourRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getRiskFactors().contains("peak_hour_congestion"));
    }

    @Test
    void testMultipleRiskFactors() {
        PaymentFailurePredictor.PaymentPredictionRequest multiRiskRequest = baseRequest;
        multiRiskRequest.setAmount(12000.0);
        multiRiskRequest.setHourOfDay(23);
        multiRiskRequest.setWeekend(true);
        multiRiskRequest.setNetworkType("3G");

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(multiRiskRequest);

        assertNotNull(prediction);
        // Multiple risk factors increase probability
        assertTrue(prediction.getFailureProbability() > 0.35);
        // At least 3 distinct risk factors identified
        assertTrue(prediction.getRiskFactors().size() >= 3);
        // High probability leads to "low" confidence
        assertTrue(
            prediction.getConfidence().equals("low") || prediction.getConfidence().equals("medium"),
            "high risk should have low or medium confidence"
        );
    }

    @Test
    void testPredictionConsistency() {
        PaymentFailurePredictor.FailurePrediction prediction1 = failurePredictor.predict(baseRequest);
        PaymentFailurePredictor.FailurePrediction prediction2 = failurePredictor.predict(baseRequest);

        assertEquals(prediction1.getFailureProbability(), prediction2.getFailureProbability());
        assertEquals(prediction1.getConfidence(), prediction2.getConfidence());
    }

    @Test
    void testPredictionBounds() {
        PaymentFailurePredictor.PaymentPredictionRequest extremeRequest = baseRequest;
        extremeRequest.setAmount(100000.0);
        extremeRequest.setHourOfDay(2);
        extremeRequest.setWeekend(true);
        extremeRequest.setNetworkType("2G");
        extremeRequest.setFraudFlagSet(true);

        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(extremeRequest);

        assertNotNull(prediction);
        assertTrue(prediction.getFailureProbability() >= 0.0);
        assertTrue(prediction.getFailureProbability() <= 1.0);
    }

    @Test
    void testRiskLevelLogic() {
        // Low risk
        PaymentFailurePredictor.PaymentPredictionRequest lowRisk = new PaymentFailurePredictor.PaymentPredictionRequest(
                "TXN001", 500.0, "P2P", "Retail", 14, false,
                "SBI", "HDFC", "Mumbai", "26-35", "26-35", "iPhone", "5G", false
        );
        PaymentFailurePredictor.FailurePrediction lowPred = failurePredictor.predict(lowRisk);
        assertEquals("LOW", lowPred.getRiskLevel());

        // High risk
        PaymentFailurePredictor.PaymentPredictionRequest highRisk = baseRequest;
        highRisk.setAmount(50000.0);
        highRisk.setHourOfDay(2);
        highRisk.setFraudFlagSet(true);
        PaymentFailurePredictor.FailurePrediction highPred = failurePredictor.predict(highRisk);
        assertEquals("MEDIUM", highPred.getRiskLevel());
    }

    @Test
    void testModelName() {
        PaymentFailurePredictor.FailurePrediction prediction = failurePredictor.predict(baseRequest);
        assertNotNull(prediction.getPredictorModel());
        assertTrue(prediction.getPredictorModel().contains("recoverflow")
                || prediction.getPredictorModel().contains("rules"));
    }
}
