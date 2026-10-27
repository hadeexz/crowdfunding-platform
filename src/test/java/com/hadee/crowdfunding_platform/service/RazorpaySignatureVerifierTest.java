package com.hadee.crowdfunding_platform.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RazorpaySignatureVerifierTest {

    private final RazorpaySignatureVerifier verifier = new RazorpaySignatureVerifier();

    @Test
    void hmac_matches_the_official_test_vector() {
        // RFC 4231, test case 2: proves our HMAC-SHA256 code is correct
        assertEquals(
                "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                verifier.hmacSha256Hex("what do ya want for nothing?", "Jefe"));
    }

    @Test
    void correct_signature_is_accepted() {
        String signature = verifier.hmacSha256Hex("order_1|pay_1", "secret");
        assertTrue(verifier.verifyPayment("order_1", "pay_1", signature, "secret"));
    }

    @Test
    void changed_payment_id_is_rejected() {
        String signature = verifier.hmacSha256Hex("order_1|pay_1", "secret");
        assertFalse(verifier.verifyPayment("order_1", "pay_2", signature, "secret"));
    }

    @Test
    void changed_order_id_is_rejected() {
        String signature = verifier.hmacSha256Hex("order_1|pay_1", "secret");
        assertFalse(verifier.verifyPayment("order_2", "pay_1", signature, "secret"));
    }

    @Test
    void wrong_secret_is_rejected() {
        String signature = verifier.hmacSha256Hex("order_1|pay_1", "secret");
        assertFalse(verifier.verifyPayment("order_1", "pay_1", signature, "other-secret"));
    }

    @Test
    void null_or_garbage_signature_is_rejected() {
        assertFalse(verifier.verifyPayment("order_1", "pay_1", null, "secret"));
        assertFalse(verifier.verifyPayment("order_1", "pay_1", "abc123", "secret"));
    }
}