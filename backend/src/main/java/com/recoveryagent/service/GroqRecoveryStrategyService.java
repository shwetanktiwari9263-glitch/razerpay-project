package com.recoveryagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoveryagent.entity.PaymentEvent;
import com.recoveryagent.entity.SystemInsight;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Groq-powered recovery advisor. It never produces a payment-risk prediction. */
@Service
@Profile("mysql")
public class GroqRecoveryStrategyService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GroqRecoveryStrategyService(RestClient.Builder builder, ObjectMapper objectMapper,
            @Value("${groq.api.key:}") String apiKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String model) {
        this.restClient = builder.baseUrl("https://api.groq.com/openai/v1").build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public StrategyResult recommend(PaymentEvent event, SystemInsight insight) {
        if (apiKey.isBlank()) return null;

        String systemPrompt = """
                You are a payment recovery advisor. ML has already calculated success probability, failure probability, and risk level.
                Never predict a payment result, produce new probabilities, or call a payment status successful/failed.
                Use only the provided Razorpay failure information for the cause. If it is unclear, set cause exactly to: Insufficient information to determine the exact cause.
                Return JSON with: cause, explanation, recommendedAction (retry|payment_link|notification|customer_support|none), alternatives (array), recoverySteps (array), priority (LOW|MEDIUM|HIGH), confidence (0 to 1), reasoning.
                Make recovery steps specific to the actual reason: funds, authentication/3DS, expired card, invalid details, temporary network/bank issue, UPI, bank decline, or repeated failures.
                """;
        String userPrompt = String.format("""
                Razorpay payment status: %s
                Error code: %s
                Error description: %s
                Payment method: %s
                Amount: %s %s
                ML success probability: %.2f
                ML failure probability: %.2f
                ML risk level: %s
                Historical context: %s
                """, event.getStatus(), event.getErrorCode(), event.getErrorDescription(), event.getGateway(), event.getAmount(),
                event.getCurrency(), java.math.BigDecimal.ONE.subtract(insight.getFailureProbability()).doubleValue(),
                insight.getFailureProbability().doubleValue(), riskLevel(insight.getFailureProbability()), insight.getAdditionalMetadata());

        Map<String, Object> request = Map.of("model", model, "temperature", 0.3,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt)));
        try {
            String response = restClient.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey).contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(String.class);
            JsonNode result = objectMapper.readTree(objectMapper.readTree(response).at("/choices/0/message/content").asText());
            Channel channel = Channel.from(result.path("recommendedAction").asText());
            if (channel == null) return null;
            return new StrategyResult(channel.value, result.path("confidence").asDouble(0.75),
                    result.path("cause").asText("Insufficient information to determine the exact cause."),
                    result.path("explanation").asText("Review the Razorpay error and guide the customer to the next step."),
                    result.path("recoverySteps").isArray() ? objectMapper.writeValueAsString(result.path("recoverySteps")) : "[]",
                    result.path("alternatives").isArray() ? objectMapper.writeValueAsString(result.path("alternatives")) : "[]",
                    result.path("priority").asText("MEDIUM"), result.path("reasoning").asText(""));
        } catch (Exception ignored) {
            return null;
        }
    }

    public String modelName() { return "groq-" + model; }

    private String riskLevel(java.math.BigDecimal probability) {
        if (probability.compareTo(java.math.BigDecimal.valueOf(.70)) >= 0) return "HIGH";
        if (probability.compareTo(java.math.BigDecimal.valueOf(.40)) >= 0) return "MEDIUM";
        return "LOW";
    }

    public record StrategyResult(String channel, double confidence, String rootCauseAnalysis, String explanation,
            String recoverySteps, String alternatives, String priority, String reasoning) { }

    private enum Channel {
        RETRY("retry"), PAYMENT_LINK("payment_link"), NOTIFICATION("notification"), CUSTOMER_SUPPORT("customer_support"), NONE("none");
        private final String value;
        Channel(String value) { this.value = value; }
        static Channel from(String value) { for (Channel channel : values()) if (channel.value.equalsIgnoreCase(value)) return channel; return null; }
    }
}
