package com.hadee.crowdfunding_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class RazorpaySignatureVerifier {

    /** Turns data + secret into a 64-character hex fingerprint (HMAC-SHA256). */
    public String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not compute HMAC", e);
        }
    }

    /** True only if the received signature equals the one we compute ourselves. */
    public boolean matches(String data, String secret, String receivedSignature) {
        if (receivedSignature == null) {
            return false;
        }
        byte[] expected = hmacSha256Hex(data, secret).getBytes(StandardCharsets.UTF_8);
        byte[] actual = receivedSignature.getBytes(StandardCharsets.UTF_8);
        // constant-time compare: does not leak how many characters matched
        return MessageDigest.isEqual(expected, actual);
    }

    /** Razorpay checkout rule: signature = HMAC(order_id + "|" + payment_id, key secret). */
    public boolean verifyPayment(String orderId, String paymentId,
                                 String signature, String keySecret) {
        return matches(orderId + "|" + paymentId, keySecret, signature);
    }
}