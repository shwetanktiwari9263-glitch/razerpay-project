package com.recoveryagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@Profile("mysql")
public class WebhookSignatureService {

    private final String secret;
    private final boolean allowUnsignedWebhooks;

    public WebhookSignatureService(@Value("${razorpay.webhook.secret:}") String secret,
            @Value("${app.webhook.allow-unsigned:false}") boolean allowUnsignedWebhooks) {
        this.secret = secret;
        this.allowUnsignedWebhooks = allowUnsignedWebhooks;
    }

    public boolean isValid(String payload, String signature) {
        if (allowUnsignedWebhooks && (signature == null || signature.isBlank())) {
            return true;
        }
        if (secret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] received = hexToBytes(signature);
            return MessageDigest.isEqual(expected, received);
        } catch (Exception exception) {
            return false;
        }
    }

    private byte[] hexToBytes(String value) {
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hexadecimal signature");
        }
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < value.length(); index += 2) {
            int high = Character.digit(value.charAt(index), 16);
            int low = Character.digit(value.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hexadecimal signature");
            }
            result[index / 2] = (byte) ((high << 4) + low);
        }
        return result;
    }
}
