package com.hadee.crowdfunding_platform.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.hadee.crowdfunding_platform.entity.enums.CampaignCategory;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;

public record CampaignResponse(
        Long id,
        Long creatorId,
        String creatorName,
        String title,
        String description,
        String imageUrl,
        CampaignCategory category,
        BigDecimal goalAmount,
        BigDecimal currentAmount,
        CampaignStatus status,
        LocalDateTime startDate,
        LocalDateTime deadline
) {}