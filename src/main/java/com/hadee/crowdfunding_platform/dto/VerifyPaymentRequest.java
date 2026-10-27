package com.hadee.crowdfunding_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyPaymentRequest(

        @NotBlank(message = "Order id is required")
        @Size(max = 50, message = "Order id is too long")
        String razorpayOrderId,

        @NotBlank(message = "Payment id is required")
        @Size(max = 50, message = "Payment id is too long")
        String razorpayPaymentId,

        @NotBlank(message = "Signature is required")
        @Size(max = 255, message = "Signature is too long")
        String razorpaySignature
) {}