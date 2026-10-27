package com.hadee.crowdfunding_platform.dto;

public record MockPaymentResponse(
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpaySignature,
        String outcome,
        String message
) {}