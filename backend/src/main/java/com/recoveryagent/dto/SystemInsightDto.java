package com.recoveryagent.dto;

import com.recoveryagent.entity.DegradationStatus;
import com.recoveryagent.entity.RootCauseCategory;
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
public class SystemInsightDto {

    private Long id;
    private BigDecimal failureProbability;
    private DegradationStatus degradationStatus;
    private String affectedGateway;
    private String affectedBank;
    private RootCauseCategory rootCauseCategory;
    private BigDecimal modelConfidence;
    private String modelVersion;
    private String additionalMetadata;
    private LocalDateTime createdAt;
}
