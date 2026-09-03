package com.recoveryagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoveryagent.entity.*;
import com.recoveryagent.repository.PaymentEventRepository;
import com.recoveryagent.repository.RecoveryOutcomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("mysql")
public class PaymentEventService {

    private final ObjectMapper objectMapper;
    private final PaymentEventRepository paymentEventRepository;
    private final RecoveryOutcomeRepository outcomeRepository;
    private final SystemInsightService insightService;
    private final AiAgentService aiAgentService;
    private final RecoveryActionService actionService;
    private final RecoveryOutcomeService outcomeService;

    public PaymentEventService(ObjectMapper objectMapper, PaymentEventRepository paymentEventRepository,
            RecoveryOutcomeRepository outcomeRepository, SystemInsightService insightService,
            AiAgentService aiAgentService, RecoveryActionService actionService,
            RecoveryOutcomeService outcomeService) {
        this.objectMapper = objectMapper;
        this.paymentEventRepository = paymentEventRepository;
        this.outcomeRepository = outcomeRepository;
        this.insightService = insightService;
        this.aiAgentService = aiAgentService;
        this.actionService = actionService;
        this.outcomeService = outcomeService;
    }

    @Transactional
    public WebhookResult process(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = text(root, "event").orElse("");
            String eventId = text(root, "event_id").orElse("local_" + UUID.randomUUID());
            if (paymentEventRepository.findByEventId(eventId).isPresent()) {
                return new WebhookResult(eventId, "duplicate", null);
            }

            JsonNode payment = root.at("/payload/payment/entity");
            PaymentEvent event = toPaymentEvent(root, payment, eventType, eventId, rawPayload);
            PaymentEvent saved = paymentEventRepository.save(event);

            if (saved.getStatus() == PaymentStatus.FAILED) {
                SystemInsight insight = insightService.analyze(saved);
                AiAgentAnalysis analysis = aiAgentService.recommend(saved, insight);
                RecoveryAction action = actionService.execute(analysis, saved);
                outcomeService.createPending(action, saved);
            } else if (saved.getStatus() == PaymentStatus.CAPTURED) {
                closePendingOutcome(saved);
            }
            return new WebhookResult(eventId, "processed", saved.getId());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid payment webhook payload: " + exception.getMessage(), exception);
        }
    }

    private void closePendingOutcome(PaymentEvent recovered) {
        List<RecoveryOutcome> candidates = outcomeRepository.findByOutcomeStatusOrderByCreatedAtDesc(OutcomeStatus.PENDING).stream()
                .filter(outcome -> matchesRecovery(outcome, recovered))
                .toList();
        // Do not credit a capture to an arbitrary recovery when one order has
        // multiple pending attempts. Leave ambiguous records for review.
        if (candidates.size() == 1) {
            outcomeService.markRecovered(candidates.get(0), recovered);
        }
    }

    private boolean matchesRecovery(RecoveryOutcome outcome, PaymentEvent recovered) {
        PaymentEvent original = outcome.getOriginalPaymentEvent();
        if (original == null) return false;
        boolean sameOrder = original.getOrderId() != null && original.getOrderId().equals(recovered.getOrderId());
        boolean sameAmount = original.getAmount() != null && original.getAmount().compareTo(recovered.getAmount()) == 0;
        String expectedPaymentId = outcome.getRecoveryAction().getNewPaymentId();
        boolean knownReplacementPayment = expectedPaymentId != null && expectedPaymentId.equals(recovered.getPaymentId());
        return knownReplacementPayment || (sameOrder && sameAmount);
    }

    private PaymentEvent toPaymentEvent(JsonNode root, JsonNode payment, String eventType,
            String eventId, String rawPayload) {
        PaymentEvent event = new PaymentEvent();
        event.setEventId(eventId);
        event.setPaymentId(text(payment, "id").orElse(text(root, "payment_id").orElse("unknown_" + eventId)));
        event.setOrderId(text(payment, "order_id").orElse(text(root, "order_id").orElse(null)));
        event.setAmount(decimal(payment, "amount").orElse(decimal(root, "amount").orElse(BigDecimal.ZERO)).movePointLeft(2));
        event.setCurrency(text(payment, "currency").orElse("INR"));
        event.setStatus(statusFor(eventType, payment));
        event.setGateway(text(payment, "method").orElse("razorpay"));
        event.setBankOrWallet(text(payment, "bank").orElse(text(payment, "wallet").orElse(null)));
        JsonNode error = payment.path("error");
        event.setErrorCode(text(payment, "error_code").orElse(text(error, "code").orElse(null)));
        event.setErrorDescription(text(payment, "error_description").orElse(text(error, "description").orElse(null)));
        event.setCustomerId(text(payment, "customer_id").orElse(null));
        event.setRawPayload(rawPayload);
        return event;
    }

    private PaymentStatus statusFor(String eventType, JsonNode payment) {
        if (eventType.endsWith("captured") || "captured".equalsIgnoreCase(text(payment, "status").orElse(""))) {
            return PaymentStatus.CAPTURED;
        }
        if (eventType.endsWith("authorized")) {
            return PaymentStatus.AUTHORIZED;
        }
        if (eventType.endsWith("refunded")) {
            return PaymentStatus.REFUNDED;
        }
        if (eventType.endsWith("failed")) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PENDING;
    }

    private Optional<String> text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.ofNullable(value.asText());
    }

    private Optional<BigDecimal> decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? Optional.empty() : Optional.of(value.decimalValue());
    }

    public record WebhookResult(String eventId, String status, Long paymentEventId) {

    }
}
