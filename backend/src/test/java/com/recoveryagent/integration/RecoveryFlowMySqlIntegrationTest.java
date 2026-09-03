package com.recoveryagent.integration;

import com.recoveryagent.entity.OutcomeStatus;
import com.recoveryagent.entity.RecoveryAction;
import com.recoveryagent.entity.RecoveryOutcome;
import com.recoveryagent.repository.AiAgentAnalysisRepository;
import com.recoveryagent.repository.RecoveryActionRepository;
import com.recoveryagent.repository.RecoveryOutcomeRepository;
import com.recoveryagent.repository.SystemInsightRepository;
import com.recoveryagent.service.PaymentEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("mysql")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_IT", matches = "true")
class RecoveryFlowMySqlIntegrationTest {
    @Autowired private PaymentEventService paymentEventService;
    @Autowired private SystemInsightRepository insightRepository;
    @Autowired private AiAgentAnalysisRepository analysisRepository;
    @Autowired private RecoveryActionRepository actionRepository;
    @Autowired private RecoveryOutcomeRepository outcomeRepository;

    @Test
    void failedPaymentIsRecoveredWhenMatchingCaptureArrives() {
        String suffix = String.valueOf(System.nanoTime());
        String orderId = "order_it_" + suffix;
        String failedEventId = "evt_it_failed_" + suffix;
        String capturedEventId = "evt_it_captured_" + suffix;
        String failedPaymentId = "pay_it_failed_" + suffix;
        PaymentEventService.WebhookResult failed = paymentEventService.process("""
                {"event":"payment.failed","event_id":"%s","payload":{"payment":{"entity":{
                "id":"%s","order_id":"%s","amount":125000,"currency":"INR","status":"failed",
                "method":"upi","bank":"HDFC","error_code":"BAD_REQUEST_ERROR",
                "error_description":"bank declined payment","customer_id":"cust_it"}}}}
                """.formatted(failedEventId, failedPaymentId, orderId));
        assertEquals("processed", failed.status());
        assertNotNull(failed.paymentEventId());
        assertTrue(insightRepository.findByPaymentEventId(failed.paymentEventId()).isPresent());
        assertTrue(analysisRepository.findByPaymentEventId(failed.paymentEventId()).isPresent());
        RecoveryAction action = actionRepository.findByPaymentEventIdOrderByTriggeredAtDesc(failed.paymentEventId()).get(0);
        assertEquals("payment_link", action.getActionType().name().toLowerCase());
        // No Razorpay credentials are provided by this local integration test,
        // so the simulated payment link remains honestly pending.
        assertEquals("pending", action.getExecutionStatus().name().toLowerCase());
        RecoveryOutcome pending = outcomeRepository.findByOriginalPaymentEventId(failed.paymentEventId()).orElseThrow();
        assertEquals(OutcomeStatus.PENDING, pending.getOutcomeStatus());
        assertFalse(pending.getRecoverySuccess());

        PaymentEventService.WebhookResult captured = paymentEventService.process("""
                {"event":"payment.captured","event_id":"%s","payload":{"payment":{"entity":{
                "id":"%s_recovery","order_id":"%s","amount":125000,"currency":"INR","status":"captured",
                "method":"upi","bank":"HDFC","customer_id":"cust_it"}}}}
                """.formatted(capturedEventId, failedPaymentId, orderId));
        assertEquals("processed", captured.status());
        RecoveryOutcome completed = outcomeRepository.findByOriginalPaymentEventId(failed.paymentEventId()).orElseThrow();
        assertEquals(OutcomeStatus.SUCCESS, completed.getOutcomeStatus());
        assertTrue(completed.getRecoverySuccess());
        assertNotNull(completed.getNewPaymentEvent());
        assertEquals(captured.paymentEventId(), completed.getNewPaymentEvent().getId());
        assertEquals(0, completed.getRecoveredAmount().compareTo(new java.math.BigDecimal("1250.00")));
    }
}
