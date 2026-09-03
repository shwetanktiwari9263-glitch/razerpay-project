package com.recoveryagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic webhook request DTO (raw payload is handled separately)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookRequestDto {

    private String eventId;
    private String eventType;
    private String paymentId;
    private String orderId;
    private Object payload;
}
