package com.hadee.crowdfunding_platform.payment;

import java.math.BigDecimal;

public interface PaymentGateway {

    /** "RAZORPAY" or "MOCK" */
    String provider();

    /** The public key the browser needs (never a secret) */
    String publicKey();

    /** Creates an order for exactly this amount and returns its id */
    String createOrder(Long donationId, Long campaignId, BigDecimal amount);

    /** True only if the payment result really came from the gateway */
    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);
}