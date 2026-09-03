package com.recoveryagent.controller;

import com.recoveryagent.service.PaymentEventService;
import com.recoveryagent.service.WebhookSignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@ActiveProfiles("mysql")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentEventService paymentEventService;

    @MockBean
    private WebhookSignatureService webhookSignatureService;

    @Test
    void acceptsPaymentWebhook() throws Exception {
        when(paymentEventService.process(anyString()))
                .thenReturn(new PaymentEventService.WebhookResult("evt_test", "processed", 7L));
        when(webhookSignatureService.isValid(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/webhooks/payment")
                .contentType("application/json")
                .header("X-Razorpay-Signature", "valid-signature")
                .content("{\"event\":\"payment.failed\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"eventId\":\"evt_test\",\"status\":\"processed\",\"paymentEventId\":7}"));
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        when(webhookSignatureService.isValid(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/payment")
                .contentType("application/json")
                .header("X-Razorpay-Signature", "invalid")
                .content("{\"event\":\"payment.failed\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":\"invalid_signature\"}"));
    }
}
