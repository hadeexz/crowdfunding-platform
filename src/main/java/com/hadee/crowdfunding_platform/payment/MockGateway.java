package com.hadee.crowdfunding_platform.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hadee.crowdfunding_platform.service.RazorpaySignatureVerifier;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "givelives.payment.provider", havingValue = "mock")
@Slf4j
public class MockGateway implements PaymentGateway {

    public static final String ORDER_PREFIX = "order_mock_";

    private final RazorpaySignatureVerifier signatureVerifier;
    private final String mockSecret;

    public MockGateway(RazorpaySignatureVerifier signatureVerifier,
                       @Value("${givelives.payment.mock-secret}") String mockSecret) {
        this.signatureVerifier = signatureVerifier;
        this.mockSecret = mockSecret;
        log.warn("MOCK payment gateway is ACTIVE. Development only: no real money moves.");
    }

    @Override
    public String provider() {
        return "MOCK";
    }

    @Override
    public String publicKey() {
        return "mock-public-key";
    }

    @Override
    public String createOrder(Long donationId, Long campaignId, BigDecimal amount) {
        return ORDER_PREFIX + randomPart();
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return signatureVerifier.verifyPayment(orderId, paymentId, signature, mockSecret);
    }

    /** Mock only: the signature the fake "bank" gives after a successful payment. */
    public String sign(String orderId, String paymentId) {
        return signatureVerifier.hmacSha256Hex(orderId + "|" + paymentId, mockSecret);
    }

    /** Mock only: a fake payment id. */
    public String newPaymentId() {
        return "pay_mock_" + randomPart();
    }

        @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return signatureVerifier.matches(rawBody, mockSecret, signature);
    }

    /** Mock only: signs a webhook body the way the real gateway would. */
    public String signWebhook(String rawBody) {
        return signatureVerifier.hmacSha256Hex(rawBody, mockSecret);
    }

    /** Mock only: a fake webhook event id. */
    public String newEventId() {
        return "evt_mock_" + randomPart();
    }

    private String randomPart() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}