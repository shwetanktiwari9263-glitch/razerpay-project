package com.recoveryagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoveryagent.entity.PaymentEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Profile("mysql")
public class RazorpayPaymentLinkService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String keyId;
    private final String keySecret;

    public RazorpayPaymentLinkService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
            @Value("${razorpay.key.id:}") String keyId,
            @Value("${razorpay.key.secret:}") String keySecret) {
        this.restClient = restClientBuilder.baseUrl("https://api.razorpay.com/v1").build();
        this.objectMapper = objectMapper;
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public PaymentLinkResult create(PaymentEvent event) {
        if (keyId.isBlank() || keySecret.isBlank()) {
            return new PaymentLinkResult("plink_mock_" + event.getPaymentId(), true, null);
        }

        Map<String, Object> request = Map.of(
                "amount", event.getAmount().movePointRight(2).intValueExact(),
                "currency", event.getCurrency(),
                "accept_partial", false,
                "description", "RecoverFlow payment recovery",
                "reference_id", event.getOrderId() == null ? event.getPaymentId() : event.getOrderId()
        );
        try {
            String response = restClient.post()
                    .uri("/payment_links")
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);
            String id = json.path("id").asText(null);
            if (id == null || id.isBlank()) {
                return new PaymentLinkResult(null, false, "Razorpay returned no payment link ID");
            }
            return new PaymentLinkResult(id, false, null);
        } catch (Exception exception) {
            return new PaymentLinkResult(null, false, exception.getMessage());
        }
    }

    public record PaymentLinkResult(String id, boolean mock, String error) {

    }
}
