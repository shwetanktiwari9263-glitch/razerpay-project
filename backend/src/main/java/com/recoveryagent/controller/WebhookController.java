package com.recoveryagent.controller;

import com.recoveryagent.service.PaymentEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import com.recoveryagent.service.WebhookSignatureService;

import java.util.Map;

@RestController
@Profile("mysql")
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final PaymentEventService paymentEventService;
    private final WebhookSignatureService signatureService;

    public WebhookController(PaymentEventService paymentEventService, WebhookSignatureService signatureService) {
        this.paymentEventService = paymentEventService;
        this.signatureService = signatureService;
    }

    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> receivePaymentWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        if (!signatureService.isValid(rawPayload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "invalid_signature"));
        }
        PaymentEventService.WebhookResult result = paymentEventService.process(rawPayload);
        return ResponseEntity.ok(Map.of(
                "eventId", result.eventId(),
                "status", result.status(),
                "paymentEventId", result.paymentEventId() == null ? "" : result.paymentEventId()
        ));
    }
}
