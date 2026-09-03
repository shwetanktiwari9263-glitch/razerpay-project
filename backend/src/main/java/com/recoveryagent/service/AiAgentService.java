package com.recoveryagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoveryagent.entity.AiAgentAnalysis;
import com.recoveryagent.entity.RecoveryChannel;
import com.recoveryagent.entity.RootCauseCategory;
import com.recoveryagent.entity.SystemInsight;
import com.recoveryagent.entity.PaymentEvent;
import com.recoveryagent.repository.AiAgentAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
@Profile("mysql")
public class AiAgentService {

    private final AiAgentAnalysisRepository repository;
    private final GroqRecoveryStrategyService groqService;
    private final ObjectMapper objectMapper;

    public AiAgentService(AiAgentAnalysisRepository repository, GroqRecoveryStrategyService groqService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.groqService = groqService;
        this.objectMapper = objectMapper;
    }

    public AiAgentAnalysis recommend(PaymentEvent event, SystemInsight insight) {
        GroqRecoveryStrategyService.StrategyResult aiResult = groqService.recommend(event, insight);

        if (aiResult != null) {
            return buildAnalysisFromAi(event, insight, aiResult);
        }

        // Fall back to rule-based recommendations
        return buildAnalysisFromRules(event, insight);
    }

    private AiAgentAnalysis buildAnalysisFromAi(PaymentEvent event, SystemInsight insight, GroqRecoveryStrategyService.StrategyResult aiResult) {
        RecoveryChannel channel = RecoveryChannel.valueOf(aiResult.channel().toUpperCase());

        AiAgentAnalysis analysis = new AiAgentAnalysis();
        analysis.setPaymentEvent(event);
        analysis.setSystemInsight(insight);
        analysis.setConfidenceScore(BigDecimal.valueOf(aiResult.confidence()));
        analysis.setRootCauseAnalysis(aiResult.rootCauseAnalysis());
        analysis.setGeneratedExplanation(aiResult.explanation());
        analysis.setSuggestedChannel(channel);
        analysis.setSuggestedAmount(event.getAmount());

        try {
            analysis.setRetryStrategy(buildRetryStrategy(channel));
            analysis.setRecoveryStrategy(objectMapper.writeValueAsString(
                    new java.util.HashMap<String, Object>() {
                {
                    put("channel", channel.name().toLowerCase());
                    put("steps", aiResult.recoverySteps());
                    put("alternatives", aiResult.alternatives());
                    put("priority", aiResult.priority());
                    put("ai_used", true);
                }
            }
            ));
        } catch (Exception e) {
            analysis.setRecoveryStrategy("{\"channel\":\"" + channel.name().toLowerCase() + "\",\"ai_used\":true}");
        }

        analysis.setAiModelName(groqService.modelName());
        analysis.setReasoningChain(toJsonText(aiResult.reasoning()));
        return repository.save(analysis);
    }

    private AiAgentAnalysis buildAnalysisFromRules(PaymentEvent event, SystemInsight insight) {
        RuleBased recommendation = getRuleBasedRecommendation(event, insight);
        RecoveryChannel channel = recommendation.channel;

        AiAgentAnalysis analysis = new AiAgentAnalysis();
        analysis.setPaymentEvent(event);
        analysis.setSystemInsight(insight);
        analysis.setConfidenceScore(recommendation.confidence);
        analysis.setRootCauseAnalysis(recommendation.cause);
        analysis.setGeneratedExplanation(recommendation.explanation);
        analysis.setSuggestedChannel(channel);
        analysis.setSuggestedAmount(event.getAmount());

        try {
            analysis.setRetryStrategy(buildRetryStrategy(channel));
            analysis.setRecoveryStrategy(objectMapper.writeValueAsString(
                    new java.util.HashMap<String, Object>() {
                {
                    put("channel", channel.name().toLowerCase());
                    put("steps", recommendation.steps);
                    put("alternatives", recommendation.alternatives);
                    put("priority", recommendation.priority);
                    put("ai_used", false);
                }
            }
            ));
        } catch (Exception e) {
            analysis.setRecoveryStrategy("{\"channel\":\"" + channel.name().toLowerCase() + "\",\"ai_used\":false}");
        }

        analysis.setAiModelName("recoverflow-rules-v1");
        analysis.setReasoningChain(toJsonText(recommendation.reasoning));
        return repository.save(analysis);
    }

    private String toJsonText(String value) {
        try {
            return objectMapper.writeValueAsString(value == null ? "" : value);
        } catch (Exception ignored) {
            return "\"\"";
        }
    }

    private RuleBased getRuleBasedRecommendation(PaymentEvent event, SystemInsight insight) {
        return switch (insight.getRootCauseCategory()) {
            case INSUFFICIENT_FUNDS ->
                new RuleBased(
                RecoveryChannel.CUSTOMER_SUPPORT,
                new BigDecimal("0.88"),
                "The customer's account does not have sufficient funds for this transaction.",
                "The payment failed because the available balance was insufficient to complete the transaction. The customer needs to add funds or use a different payment method.",
                Arrays.asList(
                "1. Contact the customer and explain the insufficient balance issue",
                "2. Ask the customer to add funds to their account",
                "3. Provide a new payment link for retry",
                "4. Suggest alternative payment method"
                ),
                Arrays.asList("wallet", "different_card", "bank_transfer"),
                "HIGH",
                "Insufficient balance typically requires customer action. Send payment link after funds are added."
                );

            case INVALID_CARD ->
                new RuleBased(
                RecoveryChannel.PAYMENT_LINK,
                new BigDecimal("0.85"),
                "The card details provided were invalid or incomplete.",
                "The payment was declined because the card information is invalid or incomplete. The customer should update their card details or use a different payment method.",
                Arrays.asList(
                "1. Notify customer of invalid card details",
                "2. Ask customer to verify and correct card information",
                "3. Send payment link with card update option",
                "4. Offer alternative payment methods (UPI, wallet, etc.)"
                ),
                Arrays.asList("upi", "wallet", "netbanking"),
                "HIGH",
                "Invalid card details require customer to provide correct information or switch payment method."
                );

            case CARD_EXPIRED ->
                new RuleBased(RecoveryChannel.PAYMENT_LINK, new BigDecimal("0.90"),
                "The card used for this payment has expired.",
                "The bank could not authorize this payment because the card is no longer valid.",
                Arrays.asList("Ask the customer to use an updated or replacement card", "Send a new payment link", "Offer UPI or netbanking if available"),
                Arrays.asList("upi", "netbanking", "replacement_card"), "HIGH",
                "The Razorpay error identifies an expired card; a retry with the same card is unlikely to help.");

            case AUTHENTICATION_FAILURE ->
                new RuleBased(RecoveryChannel.RETRY, new BigDecimal("0.86"),
                "The payment could not complete authentication.",
                "The customer needs to complete the required authentication, such as OTP or 3DS, before retrying.",
                Arrays.asList("Ask the customer to complete OTP or 3DS authentication", "Retry after authentication is available", "Offer another payment method if authentication continues to fail"),
                Arrays.asList("upi", "netbanking"), "HIGH",
                "The Razorpay error indicates authentication failed, so the next attempt must complete verification.");

            case UPI_FAILURE ->
                new RuleBased(RecoveryChannel.PAYMENT_LINK, new BigDecimal("0.78"),
                "The UPI payment could not be completed.",
                "The UPI provider or app did not complete this payment. The customer can try UPI again or choose another available method.",
                Arrays.asList("Ask the customer to retry UPI after checking their UPI app", "Send a new payment link", "Offer card or netbanking as an alternative"),
                Arrays.asList("card", "netbanking"), "MEDIUM",
                "The payment method and Razorpay error identify a UPI-specific failure.");

            case REPEATED_FAILURE ->
                new RuleBased(RecoveryChannel.CUSTOMER_SUPPORT, new BigDecimal("0.82"),
                "Multiple payment attempts have failed.",
                "Repeated retries can create duplicate attempts without resolving the underlying issue.",
                Arrays.asList("Stop repeated automatic retries", "Contact the customer before another attempt", "Offer an alternative payment method or support assistance"),
                Arrays.asList("upi", "netbanking", "different_card"), "HIGH",
                "Repeated failure requires a change of approach rather than another identical retry.");

            case BANK_ISSUE ->
                new RuleBased(
                RecoveryChannel.PAYMENT_LINK,
                new BigDecimal("0.72"),
                "The customer's bank declined the transaction.",
                "The payment was declined by the customer's bank. This could be due to bank-specific restrictions, fraud detection, or account issues. The customer should contact their bank or try a different payment method.",
                Arrays.asList(
                "1. Notify customer that bank declined the transaction",
                "2. Suggest contacting their bank to resolve restrictions",
                "3. Send payment link to retry after some time",
                "4. Offer alternative payment methods"
                ),
                Arrays.asList("upi", "netbanking", "wallet", "different_bank"),
                "HIGH",
                "Bank declines often resolve after time. Offer alternatives or retry later."
                );

            case NETWORK_ISSUE, TIMEOUT ->
                new RuleBased(
                RecoveryChannel.RETRY,
                new BigDecimal("0.70"),
                "A temporary network or server timeout occurred during payment processing.",
                "The payment failed due to a temporary network or server issue. This is typically a temporary problem and can be resolved by retrying the transaction.",
                Arrays.asList(
                "1. Wait 5-10 minutes for network stability",
                "2. Send payment link to customer for manual retry",
                "3. Automatically retry transaction after delay",
                "4. Monitor transaction status"
                ),
                Arrays.asList("alternative_gateway", "wallet"),
                "MEDIUM",
                "Temporary network issues often resolve with automatic retry. Provide customer with manual retry option."
                );

            case GATEWAY_ISSUE ->
                new RuleBased(
                RecoveryChannel.PAYMENT_LINK,
                new BigDecimal("0.65"),
                "A temporary issue occurred with the payment gateway.",
                "The payment gateway experienced a temporary issue. This is typically resolved automatically, but the customer can retry the transaction.",
                Arrays.asList(
                "1. Notify customer of gateway issue",
                "2. Wait 10-15 minutes for gateway recovery",
                "3. Send payment link for manual retry",
                "4. Monitor gateway status"
                ),
                Arrays.asList("alternative_gateway", "wallet"),
                "MEDIUM",
                "Gateway issues are usually temporary. Retry after a delay."
                );

            case RATE_LIMIT ->
                new RuleBased(
                RecoveryChannel.RETRY,
                new BigDecimal("0.60"),
                "Too many payment attempts were made in a short time (rate limit).",
                "The transaction was declined due to too many payment attempts in a short period. This is a safety measure. Please wait and retry after some time.",
                Arrays.asList(
                "1. Inform customer of rate limiting",
                "2. Wait 30 minutes before retry",
                "3. Send payment link after delay",
                "4. Suggest customer contact support if needed"
                ),
                Arrays.asList("wallet", "netbanking"),
                "MEDIUM",
                "Rate limiting requires waiting before retry. Inform customer of the delay."
                );

            case CUSTOMER_ISSUE ->
                new RuleBased(
                RecoveryChannel.CUSTOMER_SUPPORT,
                new BigDecimal("0.80"),
                "A customer-related issue prevented payment completion.",
                "The payment could not be completed due to a customer-side issue. Support team assistance is recommended.",
                Arrays.asList(
                "1. Contact customer support team",
                "2. Gather details from customer",
                "3. Troubleshoot the specific issue",
                "4. Assist customer through payment process"
                ),
                Arrays.asList("wallet", "netbanking", "alternative_card"),
                "HIGH",
                "Customer issues require support team involvement for resolution."
                );

            default ->
                new RuleBased(
                RecoveryChannel.PAYMENT_LINK,
                new BigDecimal("0.50"),
                "Insufficient information to determine the exact cause.",
                "The payment failed, but there is insufficient information to determine the exact cause. Please try the payment again or contact support for assistance.",
                Arrays.asList(
                "1. Send payment link to customer",
                "2. Customer retries transaction",
                "3. Monitor transaction status",
                "4. Escalate to support if issue persists"
                ),
                Arrays.asList("alternative_gateway", "wallet", "netbanking"),
                "MEDIUM",
                "Unknown cause requires customer retry. Escalate if repeated failures occur."
                );
        };
    }

    private String buildRetryStrategy(RecoveryChannel channel) {
        return switch (channel) {
            case RETRY ->
                "{\"max_attempts\":3,\"delay_seconds\":300,\"backoff_multiplier\":1.5}";
            case PAYMENT_LINK ->
                "{\"max_attempts\":2,\"delay_seconds\":600,\"include_alternatives\":true}";
            case CUSTOMER_SUPPORT ->
                "{\"escalate_immediately\":true,\"priority\":\"high\"}";
            default ->
                "{\"max_attempts\":1,\"delay_seconds\":0}";
        };
    }

    private static class RuleBased {

        RecoveryChannel channel;
        BigDecimal confidence;
        String cause;
        String explanation;
        List<String> steps;
        List<String> alternatives;
        String priority;
        String reasoning;

        RuleBased(RecoveryChannel channel, BigDecimal confidence, String cause, String explanation,
                List<String> steps, List<String> alternatives, String priority, String reasoning) {
            this.channel = channel;
            this.confidence = confidence;
            this.cause = cause;
            this.explanation = explanation;
            this.steps = steps;
            this.alternatives = alternatives;
            this.priority = priority;
            this.reasoning = reasoning;
        }
    }
}
