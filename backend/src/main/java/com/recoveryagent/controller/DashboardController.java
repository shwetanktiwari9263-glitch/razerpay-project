package com.recoveryagent.controller;

import com.recoveryagent.dto.DashboardSummaryDto;
import com.recoveryagent.dto.FailureAnalysisDto;
import com.recoveryagent.dto.PaymentEventDto;
import com.recoveryagent.entity.PaymentEvent;
import com.recoveryagent.entity.PaymentStatus;
import com.recoveryagent.repository.PaymentEventRepository;
import com.recoveryagent.repository.RecoveryOutcomeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@Profile("mysql")
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PaymentEventRepository paymentRepository;
    private final RecoveryOutcomeRepository outcomeRepository;

    public DashboardController(PaymentEventRepository paymentRepository,
            RecoveryOutcomeRepository outcomeRepository) {
        this.paymentRepository = paymentRepository;
        this.outcomeRepository = outcomeRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDto> getDashboardSummary(
            @RequestParam(defaultValue = "MONTH") String period) {

        LocalDateTime startDate = getStartDate(period);
        List<PaymentEvent> allPayments = paymentRepository.findAll();
        List<PaymentEvent> paymentsInPeriod = allPayments.stream()
                .filter(p -> p.getCreatedAt().isAfter(startDate))
                .collect(Collectors.toList());

        long totalPayments = paymentsInPeriod.size();
        long successfulPayments = paymentsInPeriod.stream()
                .filter(p -> p.getStatus() == PaymentStatus.CAPTURED || p.getStatus() == PaymentStatus.AUTHORIZED)
                .count();
        long failedPayments = paymentsInPeriod.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .count();

        BigDecimal successRate = totalPayments > 0
                ? BigDecimal.valueOf(successfulPayments).divide(BigDecimal.valueOf(totalPayments), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal failureRate = BigDecimal.valueOf(100).subtract(successRate);

        // Recovery metrics
        var recoveryOutcomes = outcomeRepository.findAll();
        long recoveredPayments = recoveryOutcomes.stream()
                .filter(o -> o.getRecoverySuccess() != null && o.getRecoverySuccess())
                .count();

        BigDecimal totalRecoveredAmount = recoveryOutcomes.stream()
                .filter(o -> o.getRecoveredAmount() != null)
                .map(o -> o.getRecoveredAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgRecoveryTime;
        var avgOpt = recoveryOutcomes.stream()
                .filter(o -> o.getTimeToRecovery() != null && o.getTimeToRecovery() > 0)
                .mapToLong(o -> o.getTimeToRecovery())
                .average();
        if (avgOpt.isPresent()) {
            avgRecoveryTime = BigDecimal.valueOf(avgOpt.getAsDouble() / 60).setScale(2, RoundingMode.HALF_UP);
        } else {
            avgRecoveryTime = BigDecimal.ZERO;
        }

        BigDecimal recoveryRate = failedPayments > 0
                ? BigDecimal.valueOf(recoveredPayments).divide(BigDecimal.valueOf(failedPayments), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Risk distribution
        long highRiskPayments = failedPayments;
        long mediumRiskPayments = 0;
        long lowRiskPayments = successfulPayments;

        // Top failing method
        Map<String, Long> methodCounts = paymentsInPeriod.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .collect(Collectors.groupingBy(
                        p -> p.getBankOrWallet() != null ? p.getBankOrWallet() : "Unknown",
                        Collectors.counting()
                ));

        String topFailingMethod = methodCounts.isEmpty() ? "N/A"
                : methodCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("N/A");

        BigDecimal topMethodFailureRate = methodCounts.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(methodCounts.values().stream().max(Long::compare).orElse(0L))
                        .divide(BigDecimal.valueOf(failedPayments), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        return ResponseEntity.ok(DashboardSummaryDto.builder()
                .totalPayments(totalPayments)
                .successfulPayments(successfulPayments)
                .failedPayments(failedPayments)
                .successRate(successRate.setScale(2, RoundingMode.HALF_UP))
                .failureRate(failureRate.setScale(2, RoundingMode.HALF_UP))
                .highRiskPayments(highRiskPayments)
                .recoveredPayments(recoveredPayments)
                .totalRecoveredAmount(totalRecoveredAmount.setScale(2, RoundingMode.HALF_UP))
                .averageRecoveryTime(avgRecoveryTime)
                .recoveryRatePercent(recoveryRate.setScale(2, RoundingMode.HALF_UP))
                .lowRiskCount(lowRiskPayments)
                .mediumRiskCount(mediumRiskPayments)
                .highRiskCount(highRiskPayments)
                .topFailingMethod(topFailingMethod)
                .topMethodFailureRate(topMethodFailureRate.setScale(2, RoundingMode.HALF_UP))
                .period(period)
                .lastUpdated(LocalDateTime.now().toString())
                .build());
    }

    @GetMapping("/failure-analysis")
    public ResponseEntity<FailureAnalysisDto> getFailureAnalysis(
            @RequestParam(defaultValue = "MONTH") String period) {

        LocalDateTime startDate = getStartDate(period);
        List<PaymentEvent> allPayments = paymentRepository.findAll();
        List<PaymentEvent> failedPayments = allPayments.stream()
                .filter(p -> p.getCreatedAt().isAfter(startDate))
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .collect(Collectors.toList());

        // Error code distribution
        Map<String, Long> errorCodeDistribution = failedPayments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getErrorCode() != null ? p.getErrorCode() : "UNKNOWN",
                        Collectors.counting()
                ));

        // Payment method distribution
        Map<String, Long> paymentMethodDistribution = failedPayments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getBankOrWallet() != null ? p.getBankOrWallet() : "Unknown",
                        Collectors.counting()
                ));

        // Bank failure distribution
        Map<String, Long> bankFailureDistribution = failedPayments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getBankOrWallet() != null ? p.getBankOrWallet() : "Unknown",
                        Collectors.counting()
                ));

        // Calculate failure rates
        long totalPayments = allPayments.stream()
                .filter(p -> p.getCreatedAt().isAfter(startDate))
                .count();

        Map<String, BigDecimal> errorCodeFailureRate = errorCodeDistribution.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> totalPayments > 0
                                ? BigDecimal.valueOf(e.getValue()).divide(BigDecimal.valueOf(totalPayments), 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                : BigDecimal.ZERO
                ));

        Map<String, BigDecimal> paymentMethodFailureRate = paymentMethodDistribution.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> totalPayments > 0
                                ? BigDecimal.valueOf(e.getValue()).divide(BigDecimal.valueOf(totalPayments), 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                : BigDecimal.ZERO
                ));

        // Recent failures
        List<PaymentEventDto> recentFailures = failedPayments.stream()
                .sorted(Comparator.comparing(PaymentEvent::getCreatedAt).reversed())
                .limit(10)
                .map(this::toPaymentEventDto)
                .collect(Collectors.toList());

        // Risk distribution (simplified)
        Map<String, Long> riskLevelDistribution = new HashMap<>();
        riskLevelDistribution.put("HIGH", (long) failedPayments.size());
        riskLevelDistribution.put("MEDIUM", 0L);
        riskLevelDistribution.put("LOW", 0L);

        return ResponseEntity.ok(FailureAnalysisDto.builder()
                .errorCodeDistribution(errorCodeDistribution)
                .errorCodeFailureRate(errorCodeFailureRate)
                .paymentMethodDistribution(paymentMethodDistribution)
                .paymentMethodFailureRate(paymentMethodFailureRate)
                .bankFailureDistribution(bankFailureDistribution)
                .bankFailureRate(paymentMethodFailureRate)
                .rootCauseDistribution(new HashMap<>())
                .riskLevelDistribution(riskLevelDistribution)
                .recentFailures(recentFailures)
                .recoverySuccessRateByErrorCode(new HashMap<>())
                .build());
    }

    private LocalDateTime getStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period.toUpperCase()) {
            case "TODAY" ->
                now.truncatedTo(ChronoUnit.DAYS);
            case "WEEK" ->
                now.minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
            case "MONTH" ->
                now.minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
            default ->
                LocalDateTime.of(2000, 1, 1, 0, 0);
        };
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
                .createdAt(event.getCreatedAt())
                .build();
    }
}
