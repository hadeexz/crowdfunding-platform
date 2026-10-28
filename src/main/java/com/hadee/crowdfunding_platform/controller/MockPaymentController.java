package com.hadee.crowdfunding_platform.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hadee.crowdfunding_platform.dto.MockPayRequest;
import com.hadee.crowdfunding_platform.dto.MockPaymentResponse;
import com.hadee.crowdfunding_platform.dto.MockResendRequest;
import com.hadee.crowdfunding_platform.security.UserPrincipal;
import com.hadee.crowdfunding_platform.service.MockPaymentService;

import jakarta.validation.Valid;

// DEV ONLY: exists only when givelives.payment.provider=mock
@RestController
@RequestMapping("/api/mock-payments")
@ConditionalOnProperty(name = "givelives.payment.provider", havingValue = "mock")
public class MockPaymentController {

    private final MockPaymentService mockPaymentService;

    public MockPaymentController(MockPaymentService mockPaymentService) {
        this.mockPaymentService = mockPaymentService;
    }

    @PostMapping("/pay")
    public ResponseEntity<MockPaymentResponse> pay(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MockPayRequest request) {
        return ResponseEntity.ok(mockPaymentService.simulate(principal.id(), request));
    }

    @PostMapping("/resend-webhook")
    public ResponseEntity<Map<String, String>> resend(
            @Valid @RequestBody MockResendRequest request) {
        boolean isNew = mockPaymentService.resendWebhook(request.eventId());
                return ResponseEntity.ok(Map.of("status", isNew ? "processed" : "duplicate"));
    }
}