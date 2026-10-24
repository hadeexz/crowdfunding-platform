package com.hadee.crowdfunding_platform.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.hadee.crowdfunding_platform.entity.enums.CampaignCategory;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;

public record CampaignSummaryResponse(
        Long id,
        String title,
        String imageUrl,
        CampaignCategory category,
        String creatorName,
        BigDecimal goalAmount,
        BigDecimal currentAmount,
        BigDecimal progressPercent,
        CampaignStatus status,
        LocalDateTime deadline,
        long daysLeft
) {}