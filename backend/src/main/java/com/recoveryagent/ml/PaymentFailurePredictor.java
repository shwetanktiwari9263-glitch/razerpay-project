package com.recoveryagent.ml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.*;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Payment Failure Prediction Service Predicts payment failure probability using
 * pre-trained ML model Falls back to rule-based prediction if model is
 * unavailable
 */
@Component
public class PaymentFailurePredictor {

    @Value("${ml.service.enabled:false}")
    private boolean mlServiceEnabled;

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    /**
     * Predict payment failure probability
     *
     * @param request Prediction input with transaction details
     * @return Prediction result with failure probability (0.0-1.0)
     */
    public FailurePrediction predict(PaymentPredictionRequest request) {
        try {
            if (mlServiceEnabled) {
                return predictWithModel(request);
            } else {
                return predictWithRules(request);
            }
        } catch (Exception e) {
            // Fallback to rule-based prediction on error
            return predictWithRules(request);
        }
    }

    /**
     * Calls the Python service that loads the trained scikit-learn artifact.
     */
    private FailurePrediction predictWithModel(PaymentPredictionRequest request) {
        // Uvicorn serves this local FastAPI endpoint over HTTP/1.1. Explicitly
        // selecting HTTP/1.1 prevents the JDK client's h2c upgrade attempt,
        // which Uvicorn rejects before it can validate the JSON request body.
        RestClient mlClient = RestClient.builder()
                .baseUrl(mlServiceUrl)
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .build();
        FailurePrediction prediction = mlClient.post()
                .uri("/predict")
                .body(request)
                .retrieve()
                .body(FailurePrediction.class);
        if (prediction == null || prediction.getPredictorModel() == null) {
            throw new IllegalStateException("ML service returned an invalid prediction");
        }
        return prediction;
    }

    /**
     * Rule-based failure prediction fallback Uses domain knowledge to estimate
     * failure probability
     */
    private FailurePrediction predictWithRules(PaymentPredictionRequest request) {
        double failureProbability = 0.0;
        List<String> riskFactors = new ArrayList<>();

        // Risk Factor 1: High-value transactions (>10,000 INR)
        if (request.getAmount() > 10000) {
            failureProbability += 0.15;
            riskFactors.add("high_value_transaction");
        }

        // Risk Factor 2: Night hours (0-6 AM) - lower network reliability
        if (isNightHour(request.getHourOfDay())) {
            failureProbability += 0.12;
            riskFactors.add("night_transaction");
        }

        // Risk Factor 3: Weekend transactions
        if (request.isWeekend()) {
            failureProbability += 0.08;
            riskFactors.add("weekend_transaction");
        }

        // Risk Factor 4: Specific merchant categories with higher failure rates
        if (isRiskyMerchantCategory(request.getMerchantCategory())) {
            failureProbability += 0.10;
            riskFactors.add("risky_merchant_category");
        }

        // Risk Factor 5: Specific payment methods
        if (request.getNetworkType().equals("2G") || request.getNetworkType().equals("3G")) {
            failureProbability += 0.15;
            riskFactors.add("poor_network_quality");
        }

        // Risk Factor 6: Specific sender/receiver bank combinations
        if (isRiskyBankPair(request.getSenderBank(), request.getReceiverBank())) {
            failureProbability += 0.10;
            riskFactors.add("risky_bank_pair");
        }

        // Risk Factor 7: Age group patterns
        if (request.getSenderAgeGroup().equals("18-25") || request.getSenderAgeGroup().equals("65+")) {
            failureProbability += 0.05;
            riskFactors.add("high_risk_age_group");
        }

        // Risk Factor 8: Fraud flag
        if (request.isFraudFlagSet()) {
            failureProbability += 0.25;
            riskFactors.add("fraud_suspicion");
        }

        // Risk Factor 9: Peak hours sometimes have higher congestion
        if (isPeakHour(request.getHourOfDay())) {
            failureProbability += 0.08;
            riskFactors.add("peak_hour_congestion");
        }

        // Risk Factor 10: Specific states with network issues
        if (isNetworkProneState(request.getSenderState())) {
            failureProbability += 0.10;
            riskFactors.add("state_with_network_issues");
        }

        // Normalize probability to [0, 1]
        failureProbability = Math.min(failureProbability, 0.95);
        failureProbability = Math.max(failureProbability, 0.01);

        // Determine confidence
        String confidence = failureProbability < 0.2 ? "high"
                : failureProbability < 0.5 ? "medium" : "low";

        return FailurePrediction.builder()
                .failureProbability(Math.round(failureProbability * 10000.0) / 10000.0)
                .predictorModel("recoverflow-rules-v1")
                .riskFactors(riskFactors)
                .confidence(confidence)
                .successProbability(round(1 - failureProbability))
                .riskLevel(determineRiskLevel(failureProbability))
                .build();
    }

    private boolean isNightHour(int hour) {
        return hour >= 0 && hour <= 6;
    }

    private boolean isPeakHour(int hour) {
        return (hour >= 9 && hour <= 12) || (hour >= 14 && hour <= 17) || (hour >= 19 && hour <= 22);
    }

    private boolean isRiskyMerchantCategory(String category) {
        Set<String> riskyCategories = Set.of(
                "Gambling", "Adult", "Cryptocurrency", "High_Risk_Finance"
        );
        return riskyCategories.contains(category);
    }

    private boolean isRiskyBankPair(String senderBank, String receiverBank) {
        // Banks known to have higher inter-bank settlement failures
        Set<String> bankPairs = Set.of(
                "Axis:ICICI", "ICICI:Axis", "PNB:Foreign Bank", "Yes:Small_Bank"
        );
        return bankPairs.contains(senderBank + ":" + receiverBank);
    }

    private boolean isNetworkProneState(String state) {
        Set<String> networkProneStates = Set.of(
                "Assam", "Nagaland", "Manipur", "Mizoram", "Arunachal Pradesh"
        );
        return networkProneStates.contains(state);
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String determineRiskLevel(double failureProbability) {
        if (failureProbability >= 0.70) return "HIGH";
        if (failureProbability >= 0.40) return "MEDIUM";
        return "LOW";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentPredictionRequest {

        private String transactionId;
        private double amount;
        private String transactionType;  // P2P, Merchant, etc.
        private String merchantCategory;
        private int hourOfDay;
        private boolean weekend;
        private String senderBank;
        private String receiverBank;
        private String senderState;
        private String senderAgeGroup;
        private String receiverAgeGroup;
        private String deviceType;
        private String networkType;
        private boolean fraudFlagSet;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailurePrediction {

        private double failureProbability;  // 0.0 to 1.0
        private String predictorModel;      // Model name/version
        private List<String> riskFactors;   // List of identified risk factors
        private String confidence;          // "high", "medium", "low"
        private double successProbability;  // ML output only
        private String riskLevel;           // LOW, MEDIUM, HIGH

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private double failureProbability;
            private String predictorModel;
            private List<String> riskFactors;
            private String confidence;
            private double successProbability;
            private String riskLevel;

            public Builder failureProbability(double failureProbability) {
                this.failureProbability = failureProbability;
                return this;
            }

            public Builder predictorModel(String predictorModel) {
                this.predictorModel = predictorModel;
                return this;
            }

            public Builder riskFactors(List<String> riskFactors) {
                this.riskFactors = riskFactors;
                return this;
            }

            public Builder confidence(String confidence) {
                this.confidence = confidence;
                return this;
            }

            public Builder successProbability(double successProbability) {
                this.successProbability = successProbability;
                return this;
            }

            public Builder riskLevel(String riskLevel) {
                this.riskLevel = riskLevel;
                return this;
            }

            public FailurePrediction build() {
                return new FailurePrediction(failureProbability, predictorModel, riskFactors, confidence, successProbability, riskLevel);
            }
        }
    }
}
