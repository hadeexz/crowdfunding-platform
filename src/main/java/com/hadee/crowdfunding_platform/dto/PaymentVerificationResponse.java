package com.hadee.crowdfunding_platform.dto;

import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;

public record PaymentVerificationResponse(
        Long donationId,
        boolean verified,
        DonationStatus donationStatus,
        String message
) {}