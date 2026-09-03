package com.recoveryagent.service;

import com.recoveryagent.entity.DegradationStatus;
import com.recoveryagent.entity.PaymentEvent;
import com.recoveryagent.entity.RootCauseCategory;
import com.recoveryagent.entity.SystemInsight;
import com.recoveryagent.repository.SystemInsightRepository;
import com.recoveryagent.repository.PaymentEventRepository;
import com.recoveryagent.ml.PaymentFailurePredictor;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Service
@Profile("mysql")
public class SystemInsightService {

    private final SystemInsightRepository repository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentFailurePredictor predictor;

    public SystemInsightService(SystemInsightRepository repository, PaymentEventRepository paymentEventRepository,
            PaymentFailurePredictor predictor) {
        this.repository = repository;
        this.paymentEventRepository = paymentEventRepository;
        this.predictor = predictor;
    }

    public SystemInsight analyze(PaymentEvent event) {
        RootCauseCategory category = categorize(event.getErrorCode(), event.getErrorDescription());

        PaymentFailurePredictor.FailurePrediction prediction = predictor.predict(extractFeatures(event));
        BigDecimal failureProbability = BigDecimal.valueOf(prediction.getFailureProbability());
        BigDecimal successProbability = BigDecimal.valueOf(prediction.getSuccessProbability());

        SystemInsight insight = new SystemInsight();
        insight.setPaymentEvent(event);
        insight.setFailureProbability(failureProbability);
        insight.setDegradationStatus(
                failureProbability.compareTo(new BigDecimal("0.75")) >= 0
                ? DegradationStatus.CRITICAL
                : failureProbability.compareTo(new BigDecimal("0.50")) >= 0
                ? DegradationStatus.DEGRADED
                : DegradationStatus.HEALTHY
        );
        insight.setAffectedGateway(event.getGateway());
        insight.setAffectedBank(event.getBankOrWallet());
        insight.setRootCauseCategory(category);

        BigDecimal modelConfidence = switch (prediction.getConfidence()) {
            case "high" -> new BigDecimal("0.85");
            case "medium" -> new BigDecimal("0.70");
            default -> new BigDecimal("0.55");
        };

        insight.setModelConfidence(modelConfidence);
        insight.setModelVersion(prediction.getPredictorModel());

        try {
            insight.setAdditionalMetadata("{"
                    + "\"success_probability\":" + successProbability + ","
                    + "\"failure_probability\":" + failureProbability + ","
                    + "\"model_confidence\":" + modelConfidence + ","
                    + "\"risk_level\":\"" + prediction.getRiskLevel() + "\","
                    + "\"historical_failure_count\":" + historicalFailureCount(event) + ","
                    + "\"risk_factors\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(prediction.getRiskFactors())
                    + "}");
        } catch (Exception e) {
            insight.setAdditionalMetadata("{}");
        }

        return repository.save(insight);
    }

    /** Converts webhook data and previous payment events into ML features only. */
    private PaymentFailurePredictor.PaymentPredictionRequest extractFeatures(PaymentEvent event) {
        LocalDateTime occurredAt = event.getCreatedAt() == null ? LocalDateTime.now() : event.getCreatedAt();
        long priorFailures = historicalFailureCount(event);
        return new PaymentFailurePredictor.PaymentPredictionRequest(event.getPaymentId(), event.getAmount().doubleValue(),
                "MERCHANT", "General", occurredAt.getHour(),
                occurredAt.getDayOfWeek() == DayOfWeek.SATURDAY || occurredAt.getDayOfWeek() == DayOfWeek.SUNDAY,
                event.getBankOrWallet() == null ? "unknown" : event.getBankOrWallet(), "razorpay", "unknown", "unknown", "unknown",
                "unknown", "unknown", priorFailures >= 3);
    }

    private long historicalFailureCount(PaymentEvent event) {
        if (event.getCustomerId() == null) return 0;
        return paymentEventRepository.findByCustomerId(event.getCustomerId()).stream()
                .filter(previous -> !previous.getId().equals(event.getId()))
                .filter(previous -> previous.getStatus() == com.recoveryagent.entity.PaymentStatus.FAILED)
                .count();
    }

    private String determineRiskLevel(BigDecimal failureProbability) {
        if (failureProbability.compareTo(new BigDecimal("0.70")) >= 0) {
            return "HIGH";
        }
        if (failureProbability.compareTo(new BigDecimal("0.40")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private RootCauseCategory categorize(String errorCode, String description) {
        String value = ((errorCode == null ? "" : errorCode) + " "
                + (description == null ? "" : description)).toLowerCase();

        // Customer financial issues
        if (value.contains("insufficient") || value.contains("fund")) {
            return RootCauseCategory.INSUFFICIENT_FUNDS;
        }

        // Card-related issues
        if (value.contains("expired")) {
            return RootCauseCategory.CARD_EXPIRED;
        }
        if (value.contains("invalid") || value.contains("incorrect") || value.contains("wrong")) {
            return RootCauseCategory.INVALID_CARD;
        }

        // Authentication issues
        if (value.contains("authentication") || value.contains("3ds") || value.contains("verification")
                || value.contains("otp") || value.contains("pin")) {
            return RootCauseCategory.AUTHENTICATION_FAILURE;
        }

        // Network and timeout issues
        if (value.contains("timeout") || value.contains("timed out")) {
            return RootCauseCategory.TIMEOUT;
        }
        if (value.contains("network") || value.contains("connection") || value.contains("temporary")) {
            return RootCauseCategory.TEMPORARY_NETWORK;
        }

        // Rate limiting
        if (value.contains("rate") || value.contains("limit") || value.contains("quota")) {
            return RootCauseCategory.RATE_LIMIT;
        }

        // Bank-related issues
        if (value.contains("bank") || value.contains("declin") || value.contains("bank_decline")) {
            return RootCauseCategory.BANK_ISSUE;
        }

        // Gateway issues
        if (value.contains("gateway")) {
            return RootCauseCategory.GATEWAY_ISSUE;
        }

        // UPI-specific issues
        if (value.contains("upi") || value.contains("vpa")) {
            return RootCauseCategory.UPI_FAILURE;
        }

        // Repeated failures
        if (value.contains("repeated") || value.contains("multiple") || value.contains("consistent")) {
            return RootCauseCategory.REPEATED_FAILURE;
        }

        // Generic customer issues
        if (value.contains("customer") || value.contains("user")) {
            return RootCauseCategory.CUSTOMER_ISSUE;
        }

        return RootCauseCategory.UNKNOWN;
    }
}
