package com.oneshop.controller.client;

import com.oneshop.service.payment.payos.PayosException;
import com.oneshop.service.payment.payos.PayosWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receiver of payOS webhooks: {@code POST /api/payments/payos/webhook}. Public (payOS is not a logged-in user) and
 * exempt from CSRF; its only protection is the HMAC signature the SDK verifies against the checksum key. Nothing in
 * the body is trusted before that.
 */
@RestController
@RequestMapping("/api/payments/payos")
@ConditionalOnProperty(name = "oneshop.payment.provider", havingValue = "PAYOS")
public class PayosWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayosWebhookController.class);

    private final PayosWebhookService webhooks;

    public PayosWebhookController(PayosWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping(value = "/webhook", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> receive(@RequestBody String body) {
        try {
            return switch (webhooks.handle(body)) {
                case INVALID_SIGNATURE -> ResponseEntity.badRequest().body(Map.of("success", false));
                case ACCEPTED, DUPLICATE -> ResponseEntity.ok(Map.of("success", true));
            };
        } catch (PayosException.NotConfigured ex) {
            return ResponseEntity.status(503).body(Map.of("success", false));
        } catch (RuntimeException ex) {
            // not acknowledged: payOS retries a webhook that did not get a 2xx
            log.error("payOS webhook processing failed", ex);
            return ResponseEntity.status(500).body(Map.of("success", false));
        }
    }
}
