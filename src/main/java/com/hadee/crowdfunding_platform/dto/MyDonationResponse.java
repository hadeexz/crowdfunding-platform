package com.hadee.crowdfunding_platform.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;

public record MyDonationResponse(
        Long donationId,
        Long campaignId,
        String campaignTitle,
        BigDecimal amount,
        boolean anonymous,
        DonationStatus status,
        PaymentStatus paymentStatus,
        String note,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}