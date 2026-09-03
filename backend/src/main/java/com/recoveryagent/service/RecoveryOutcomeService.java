package com.recoveryagent.service;

import com.recoveryagent.entity.OutcomeStatus;
import com.recoveryagent.entity.PaymentEvent;
import com.recoveryagent.entity.RecoveryAction;
import com.recoveryagent.entity.RecoveryOutcome;
import com.recoveryagent.repository.RecoveryOutcomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Profile("mysql")
public class RecoveryOutcomeService {

    private final RecoveryOutcomeRepository repository;

    public RecoveryOutcomeService(RecoveryOutcomeRepository repository) {
        this.repository = repository;
    }

    public RecoveryOutcome createPending(RecoveryAction action, PaymentEvent original) {
        RecoveryOutcome outcome = new RecoveryOutcome();
        outcome.setRecoveryAction(action);
        outcome.setOriginalPaymentEvent(original);
        outcome.setOutcomeStatus(OutcomeStatus.PENDING);
        outcome.setRecoverySuccess(false);
        outcome.setRecoveryAttemptCount(1);
        outcome.setFeedbackNotes("{\"status\":\"awaiting_customer_payment\"}");
        return repository.save(outcome);
    }

    public RecoveryOutcome markRecovered(RecoveryOutcome outcome, PaymentEvent recovered) {
        LocalDateTime started = outcome.getRecoveryAction().getTriggeredAt();
        if (started == null) {
            started = LocalDateTime.now();
        }
        int seconds = Math.max(0, (int) Duration.between(started, LocalDateTime.now()).getSeconds());

        outcome.setNewPaymentEvent(recovered);
        outcome.setOutcomeStatus(OutcomeStatus.SUCCESS);
        outcome.setRecoverySuccess(true);
        outcome.setRecoveredAmount(recovered.getAmount());
        outcome.setTimeToRecovery(seconds);
        outcome.setRecoveryVelocityHours(BigDecimal.valueOf(seconds / 3600.0).setScale(2, java.math.RoundingMode.HALF_UP));
        outcome.setFeedbackNotes("{\"matched_by\":\"order_id\"}");
        return repository.save(outcome);
    }
}
