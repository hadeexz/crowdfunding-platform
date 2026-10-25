package com.hadee.crowdfunding_platform.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateDonationRequest(

        @NotNull(message = "Campaign id is required")
        @Positive(message = "Campaign id is invalid")
        Long campaignId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 10, fraction = 2,
                message = "Amount can have at most 10 digits and 2 decimal places")
        BigDecimal amount,

        // if the client leaves this out, it becomes false (not anonymous)
        boolean anonymous
) {}