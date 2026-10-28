package com.hadee.crowdfunding_platform.payment;

import java.math.BigDecimal;

import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hadee.crowdfunding_platform.config.RazorpayProperties;
import com.hadee.crowdfunding_platform.exception.PaymentGatewayException;
import com.hadee.crowdfunding_platform.service.RazorpaySignatureVerifier;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "givelives.payment.provider", havingValue = "razorpay")
@RequiredArgsConstructor
public class RazorpayGateway implements PaymentGateway {

    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final RazorpaySignatureVerifier signatureVerifier;

    @Override
    public String provider() {
        return "RAZORPAY";
    }

    @Override
    public String publicKey() {
        return razorpayProperties.keyId();
    }

    @Override
    public String createOrder(Long donationId, Long campaignId, BigDecimal amount) {
        try {
            // Razorpay counts in paise: rupees x 100
            long paise = amount.movePointRight(2).longValueExact();

            JSONObject notes = new JSONObject();
            notes.put("donation_id", donationId);
            notes.put("campaign_id", campaignId);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", paise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "donation_" + donationId);
            orderRequest.put("notes", notes);

            Order order = razorpayClient.orders.create(orderRequest);
            return order.get("id");
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Razorpay order creation failed: " + e.getMessage());
        }
    }

        @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        if (!isConfigured(razorpayProperties.keySecret())) {
            return false;
        }
        return signatureVerifier.verifyPayment(orderId, paymentId, signature, razorpayProperties.keySecret());
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (!isConfigured(razorpayProperties.webhookSecret())) {
            return false;
        }
        return signatureVerifier.matches(rawBody, razorpayProperties.webhookSecret(), signature);
    }

    // The default placeholder "not-set..." is publicly known, so it must never act as a real secret.
    private boolean isConfigured(String secret) {
        return secret != null && !secret.isBlank() && !secret.startsWith("not-set");
    }
}