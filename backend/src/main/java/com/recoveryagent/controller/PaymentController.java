package com.recoveryagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoveryagent.dto.*;
import com.recoveryagent.entity.PaymentEvent;
import com.recoveryagent.entity.PaymentStatus;
import com.recoveryagent.repository.PaymentEventRepository;
import com.recoveryagent.repository.SystemInsightRepository;
import com.recoveryagent.repository.AiAgentAnalysisRepository;
import com.recoveryagent.repository.RecoveryOutcomeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@Profile("mysql")
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentEventRepository paymentRepository;
    private final SystemInsightRepository insightRepository;
    private final AiAgentAnalysisRepository analysisRepository;
    private final RecoveryOutcomeRepository outcomeRepository;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentEventRepository paymentRepository,
            SystemInsightRepository insightRepository,
            AiAgentAnalysisRepository analysisRepository,
            RecoveryOutcomeRepository outcomeRepository,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.insightRepository = insightRepository;
        this.analysisRepository = analysisRepository;
        this.outcomeRepository = outcomeRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<PageDto<PaymentEventDto>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        Page<PaymentEvent> paymentPage;
        if (status != null && !status.isEmpty()) {
            try {
                PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
                paymentPage = paymentRepository.findByStatus(paymentStatus, PageRequest.of(page, size, Sort.by("createdAt").descending()));
            } catch (IllegalArgumentException e) {
                paymentPage = paymentRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
            }
        } else {
            paymentPage = paymentRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        }

        List<PaymentEventDto> content = paymentPage.getContent().stream()
                .map(this::toPaymentEventDto)
                .collect(Collectors.toList());

        PageDto<PaymentEventDto> pageDto = PageDto.<PaymentEventDto>builder()
                .content(content)
                .totalElements(paymentPage.getTotalElements())
                .totalPages(paymentPage.getTotalPages())
                .pageNumber(page)
                .pageSize(size)
                .hasNext(paymentPage.hasNext())
                .hasPrevious(paymentPage.hasPrevious())
                .build();

        return ResponseEntity.ok(pageDto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDetailDto> getPaymentDetail(@PathVariable Long id) {
        return paymentRepository.findById(id)
                .map(this::toPaymentDetailDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/prediction")
    public ResponseEntity<PredictionResponseDto> getPaymentPrediction(@PathVariable Long id) {
        return paymentRepository.findById(id)
                .flatMap(payment -> insightRepository.findByPaymentEventId(id).map(insight
                -> PredictionResponseDto.builder()
                        .paymentId(id)
                        .paymentIdentifier(payment.getPaymentId())
                        .successProbability(
                                insight.getFailureProbability() != null
                                ? java.math.BigDecimal.ONE.subtract(insight.getFailureProbability())
                                : java.math.BigDecimal.ZERO
                        )
                        .failureProbability(insight.getFailureProbability())
                        .riskLevel(determineRiskLevel(insight.getFailureProbability()))
                        .modelConfidence(insight.getModelConfidence())
                        .modelVersion(insight.getModelVersion())
                        .modelName("Payment Failure Predictor")
                        .explanation(generatePredictionExplanation(insight))
                        .predictionTimestamp(System.currentTimeMillis())
                        .build()
        ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/ai-analysis")
    public ResponseEntity<AiAnalysisResponseDto> getPaymentAiAnalysis(@PathVariable Long id) {
        return paymentRepository.findById(id)
                .flatMap(payment -> analysisRepository.findByPaymentEventId(id).map(analysis -> {
            try {
                com.fasterxml.jackson.databind.JsonNode strategy = objectMapper.readTree(analysis.getRecoveryStrategy());
                List<String> steps = jsonStringList(strategy.path("steps"));
                List<String> alternatives = jsonStringList(strategy.path("alternatives"));

                return AiAnalysisResponseDto.builder()
                        .paymentId(id)
                        .paymentIdentifier(payment.getPaymentId())
                        .cause(analysis.getRootCauseAnalysis())
                        .explanation(analysis.getGeneratedExplanation())
                        .technicalDetails("Error Code: " + payment.getErrorCode() + ", Bank: " + payment.getBankOrWallet())
                        .recommendedAction(analysis.getSuggestedChannel())
                        .recoveryStrategy(analysis.getRecoveryStrategy())
                        .recommendedActions(steps)
                        .recoverySteps(steps)
                        .alternatives(alternatives)
                        .priority(strategy.path("priority").asText("MEDIUM"))
                        .confidenceScore(analysis.getConfidenceScore())
                        .aiModel(analysis.getAiModelName())
                        .reasoning(analysis.getReasoningChain())
                        .analysisTimestamp(System.currentTimeMillis())
                        .build();
            } catch (Exception e) {
                return AiAnalysisResponseDto.builder()
                        .paymentId(id)
                        .paymentIdentifier(payment.getPaymentId())
                        .cause("Error processing analysis")
                        .explanation("There was an error processing the AI analysis")
                        .recommendedAction(analysis.getSuggestedChannel())
                        .priority("MEDIUM")
                        .confidenceScore(analysis.getConfidenceScore())
                        .analysisTimestamp(System.currentTimeMillis())
                        .build();
            }
        }))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private List<String> jsonStringList(com.fasterxml.jackson.databind.JsonNode value) {
        if (value.isTextual()) {
            try { value = objectMapper.readTree(value.asText()); } catch (Exception ignored) { }
        }
        if (!value.isArray()) return List.of();
        List<String> result = new java.util.ArrayList<>();
        value.forEach(item -> result.add(item.asText()));
        return result;
    }


    private PaymentEventDto toPaymentEventDto(PaymentEvent event) {
        return PaymentEventDto.builder()
                .id(event.getId())
                .eventId(event.getEventId())
                .paymentId(event.getPaymentId())
                .orderId(event.getOrderId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .status(event.getStatus())
                .gateway(event.getGateway())
                .bankOrWallet(event.getBankOrWallet())
                .errorCode(event.getErrorCode())
                .errorDescription(event.getErrorDescription())
                .customerId(event.getCustomerId())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }

    private PaymentDetailDto toPaymentDetailDto(PaymentEvent event) {
        var insight = insightRepository.findByPaymentEventId(event.getId());
        var analysis = analysisRepository.findByPaymentEventId(event.getId());

        return PaymentDetailDto.builder()
                .id(event.getId())
                .paymentId(event.getPaymentId())
                .orderId(event.getOrderId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .status(event.getStatus())
                .gateway(event.getGateway())
                .bankOrWallet(event.getBankOrWallet())
                .errorCode(event.getErrorCode())
                .errorDescription(event.getErrorDescription())
                .customerId(event.getCustomerId())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                // ML Prediction
                .failureProbability(insight.map(i -> i.getFailureProbability()).orElse(null))
                .successProbability(insight.map(i
                        -> i.getFailureProbability() != null
                ? java.math.BigDecimal.ONE.subtract(i.getFailureProbability())
                : java.math.BigDecimal.ZERO
                ).orElse(null))
                .riskLevel(insight.map(i -> determineRiskLevel(i.getFailureProbability())).orElse("UNKNOWN"))
                .mlModelVersion(insight.map(i -> i.getModelVersion()).orElse(null))
                .modelConfidence(insight.map(i -> i.getModelConfidence()).orElse(null))
                // AI Analysis
                .aiCauseAnalysis(analysis.map(a -> a.getRootCauseAnalysis()).orElse(null))
                .aiExplanation(analysis.map(a -> a.getGeneratedExplanation()).orElse(null))
                .aiRecommendedAction(analysis.map(a -> a.getSuggestedChannel().name()).orElse(null))
                .aiRecoveryStrategy(analysis.map(a -> a.getRecoveryStrategy()).orElse(null))
                .aiPriority(analysis.map(this::priorityFromStrategy).orElse(null))
                .aiConfidenceScore(analysis.map(a -> a.getConfidenceScore()).orElse(null))
                .build();
    }

    private String determineRiskLevel(java.math.BigDecimal probability) {
        if (probability == null) {
            return "UNKNOWN";
        }
        if (probability.compareTo(java.math.BigDecimal.valueOf(0.7)) >= 0) {
            return "HIGH";
        }
        if (probability.compareTo(java.math.BigDecimal.valueOf(0.4)) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String priorityFromStrategy(com.recoveryagent.entity.AiAgentAnalysis analysis) {
        try { return objectMapper.readTree(analysis.getRecoveryStrategy()).path("priority").asText("MEDIUM"); }
        catch (Exception ignored) { return "MEDIUM"; }
    }

    private String generatePredictionExplanation(com.recoveryagent.entity.SystemInsight insight) {
        return "Prediction based on " + insight.getRootCauseCategory().name().toLowerCase()
                + " with " + (insight.getModelConfidence() != null
                ? insight.getModelConfidence().multiply(java.math.BigDecimal.valueOf(100)) : java.math.BigDecimal.ZERO)
                + "% confidence.";
    }

}
