package com.hadee.crowdfunding_platform.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.hadee.crowdfunding_platform.dto.MockPayRequest;
import com.hadee.crowdfunding_platform.dto.MockPaymentResponse;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.PaymentTransaction;
import com.hadee.crowdfunding_platform.entity.WebhookEvent;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;
import com.hadee.crowdfunding_platform.exception.ConflictException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.payment.MockGateway;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;
import com.hadee.crowdfunding_platform.repository.WebhookEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnProperty(name = "givelives.payment.provider", havingValue = "mock")
@RequiredArgsConstructor
public class MockPaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final MockGateway mockGateway;
    private final WebhookService webhookService;

    // Pretends to be the bank page. Like the real gateway, it ALSO sends a signed
    // webhook. NOT @Transactional: the webhook is saved on its own, like a real delivery.
    public MockPaymentResponse simulate(Long donorId, MockPayRequest request) {

        String orderId = request.razorpayOrderId();
        if (!orderId.startsWith(MockGateway.ORDER_PREFIX)) {
            throw new IllegalArgumentException("Not a mock order");
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findWithDonationByRazorpayOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        Donation donation = transaction.getDonation();
        if (!donation.getDonor().getId().equals(donorId)) {
            throw new ResourceNotFoundException("Payment not found");
        }
        if (transaction.getStatus() != PaymentStatus.CREATED) {
            throw new ConflictException("This payment was already processed");
        }

        boolean success = request.outcome() == MockPayRequest.Outcome.SUCCESS;
        String paymentId = mockGateway.newPaymentId();
        String eventId = mockGateway.newEventId();

        String eventJson = buildEvent(orderId, paymentId, donation.getAmount(), success);
        webhookService.receive(
                eventJson.getBytes(StandardCharsets.UTF_8),
                mockGateway.signWebhook(eventJson),
                eventId);

        if (!success) {
            return new MockPaymentResponse(orderId, null, null, "FAILURE",
                    "Simulated payment failure", eventId);
        }
        return new MockPaymentResponse(orderId, paymentId, mockGateway.sign(orderId, paymentId),
                "SUCCESS", "Simulated payment success", eventId);
    }

    // Test helper: the gateway delivers the SAME event again (retry). Must be ignored.
    public boolean resendWebhook(String eventId) {
        WebhookEvent stored = webhookEventRepository.findByRazorpayEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook event not found"));
        String body = stored.getPayload();
        return webhookService.receive(
                body.getBytes(StandardCharsets.UTF_8),
                mockGateway.signWebhook(body),
                eventId);
    }

    // Same shape as a real Razorpay payment event
    private String buildEvent(String orderId, String paymentId, BigDecimal amount, boolean success) {
        JSONObject payment = new JSONObject();
        payment.put("id", paymentId);
        payment.put("entity", "payment");
        payment.put("amount", amount.movePointRight(2).longValueExact());
        payment.put("currency", "INR");
        payment.put("status", success ? "captured" : "failed");
        payment.put("order_id", orderId);
        payment.put("error_description", success ? JSONObject.NULL : "Simulated payment failure");

        JSONObject payload = new JSONObject();
        payload.put("payment", new JSONObject().put("entity", payment));

        JSONObject event = new JSONObject();
        event.put("entity", "event");
        event.put("event", success ? "payment.captured" : "payment.failed");
        event.put("payload", payload);
        event.put("created_at", Instant.now().getEpochSecond());
        return event.toString();
    }
}