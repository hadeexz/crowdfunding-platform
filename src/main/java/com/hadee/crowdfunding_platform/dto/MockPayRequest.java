package com.hadee.crowdfunding_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MockPayRequest(

        @NotBlank(message = "Order id is required")
        @Size(max = 50, message = "Order id is too long")
        String razorpayOrderId,

        @NotNull(message = "Outcome is required (SUCCESS or FAILURE)")
        Outcome outcome
) {
    public enum Outcome { SUCCESS, FAILURE }
}