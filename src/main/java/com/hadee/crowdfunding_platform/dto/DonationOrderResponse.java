package com.hadee.crowdfunding_platform.dto;

import java.math.BigDecimal;

import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;

public record DonationOrderResponse(
        Long donationId,
        Long campaignId,
        BigDecimal amount,
        BigDecimal platformFee,
        BigDecimal netAmount,
        String currency,
        String razorpayOrderId,
        String razorpayKeyId,
        DonationStatus status
) {}