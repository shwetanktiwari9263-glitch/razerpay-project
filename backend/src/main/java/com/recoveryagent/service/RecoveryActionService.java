package com.recoveryagent.service;

import com.recoveryagent.entity.AiAgentAnalysis;
import com.recoveryagent.entity.ExecutionStatus;
import com.recoveryagent.entity.PaymentEvent;
import com.recoveryagent.entity.RecoveryAction;
import com.recoveryagent.entity.RecoveryChannel;
import com.recoveryagent.repository.RecoveryActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;

@Service
@Profile("mysql")
public class RecoveryActionService {

    private final RecoveryActionRepository repository;
    private final RazorpayPaymentLinkService razorpayPaymentLinkService;

    public RecoveryActionService(RecoveryActionRepository repository,
            RazorpayPaymentLinkService razorpayPaymentLinkService) {
        this.repository = repository;
        this.razorpayPaymentLinkService = razorpayPaymentLinkService;
    }

    public RecoveryAction execute(AiAgentAnalysis analysis, PaymentEvent event) {
        RecoveryChannel channel = analysis.getSuggestedChannel();
        RecoveryAction action = new RecoveryAction();
        action.setAiAgentAnalysis(analysis);
        action.setPaymentEvent(event);
        action.setActionType(channel);
        action.setExecutionStatus(channel == RecoveryChannel.NONE ? ExecutionStatus.CANCELLED : ExecutionStatus.PENDING);
        action.setRulesApplied("recovery-policy-v1");
        if (channel == RecoveryChannel.PAYMENT_LINK) {
            RazorpayPaymentLinkService.PaymentLinkResult link = razorpayPaymentLinkService.create(event);
            action.setNewRazorpayLinkId(link.id());
            action.setExecutionStatus(link.error() == null && !link.mock() ? ExecutionStatus.EXECUTED
                    : link.error() == null ? ExecutionStatus.PENDING : ExecutionStatus.FAILED);
            action.setExecutionError(link.error());
            action.setActionMetadata("{\"mock\":" + link.mock() + ",\"channel\":\"payment_link\"}");
        } else {
            action.setActionMetadata("{\"channel\":\"" + channel.name().toLowerCase()
                    + "\",\"delivery_status\":\"awaiting_channel_execution\"}");
        }
        if (action.getExecutionStatus() == ExecutionStatus.EXECUTED) {
            action.setExecutedAt(LocalDateTime.now());
        }
        return repository.save(action);
    }
}
