package com.hadee.crowdfunding_platform.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hadee.crowdfunding_platform.service.WebhookService;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    // Public URL: no login token. The signature is the proof of identity.
    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, String>> razorpay(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {

        boolean isNew = webhookService.receive(body, signature, eventId);
        return ResponseEntity.ok(Map.of("status", isNew ? "received" : "duplicate"));
    }
}