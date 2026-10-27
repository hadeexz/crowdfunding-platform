package com.hadee.crowdfunding_platform.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.hadee.crowdfunding_platform.service.RazorpaySignatureVerifier;

class MockGatewayTest {

    private final MockGateway gateway =
            new MockGateway(new RazorpaySignatureVerifier(), "test-secret");

    @Test
    void order_id_has_mock_prefix_and_fits_the_database_column() {
        String id = gateway.createOrder(1L, 2L, new BigDecimal("100"));
        assertTrue(id.startsWith(MockGateway.ORDER_PREFIX));
        assertTrue(id.length() <= 50);
    }

    @Test
    void signed_payment_verifies() {
        String payId = gateway.newPaymentId();
        String signature = gateway.sign("order_mock_1", payId);
        assertTrue(gateway.verifyPaymentSignature("order_mock_1", payId, signature));
    }

    @Test
    void tampered_payment_is_rejected() {
        String payId = gateway.newPaymentId();
        String signature = gateway.sign("order_mock_1", payId);
        assertFalse(gateway.verifyPaymentSignature("order_mock_1", "pay_mock_other", signature));
        assertFalse(gateway.verifyPaymentSignature("order_mock_1", payId, "abc123"));
    }
}