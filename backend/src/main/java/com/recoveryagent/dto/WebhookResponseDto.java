package com.recoveryagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard webhook response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookResponseDto {

    private String eventId;
    private String status; // "processed", "duplicate", "invalid_signature", "error"
    private Long paymentEventId;
    private String message;
    private Long timestamp;
}
