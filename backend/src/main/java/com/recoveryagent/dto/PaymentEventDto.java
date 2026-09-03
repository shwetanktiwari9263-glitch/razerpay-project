package com.recoveryagent.dto;

import com.recoveryagent.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEventDto {

    private Long id;
    private String eventId;
    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String gateway;
    private String bankOrWallet;
    private String errorCode;
    private String errorDescription;
    private String customerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Nested DTOs for complete information
    private SystemInsightDto systemInsight;
    private AiAgentAnalysisDto aiAnalysis;
}
