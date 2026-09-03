package com.recoveryagent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoveryagent.entity.AiAgentAnalysis;
import com.recoveryagent.repository.AiAgentAnalysisRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Dashboard feed for Groq recovery analyses created after real payment failures. */
@RestController
@Profile("mysql")
@RequestMapping("/api/dashboard")
public class RecoveryAnalysisDashboardController {
    private final AiAgentAnalysisRepository repository;
    private final ObjectMapper objectMapper;

    public RecoveryAnalysisDashboardController(AiAgentAnalysisRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/ai-recovery-analyses")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentAnalyses() {
        return repository.findAll().stream().sorted(Comparator.comparing(AiAgentAnalysis::getCreatedAt).reversed()).limit(6)
                .map(this::toResponse).toList();
    }

    private Map<String, Object> toResponse(AiAgentAnalysis analysis) {
        JsonNode strategy;
        try { strategy = objectMapper.readTree(analysis.getRecoveryStrategy()); }
        catch (Exception ignored) { strategy = objectMapper.createObjectNode(); }
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("paymentId", analysis.getPaymentEvent().getPaymentId());
        response.put("paymentMethod", analysis.getPaymentEvent().getGateway());
        response.put("cause", analysis.getRootCauseAnalysis());
        response.put("explanation", analysis.getGeneratedExplanation());
        response.put("recommendedAction", analysis.getSuggestedChannel().name());
        response.put("recoverySteps", stringList(strategy.path("steps")));
        response.put("alternatives", stringList(strategy.path("alternatives")));
        response.put("priority", strategy.path("priority").asText("MEDIUM"));
        response.put("aiModel", analysis.getAiModelName());
        response.put("createdAt", analysis.getCreatedAt().toString());
        return response;
    }

    private List<String> stringList(JsonNode node) {
        try { if (node.isTextual()) node = objectMapper.readTree(node.asText()); }
        catch (Exception ignored) { return List.of(); }
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }
}
