package com.hadee.crowdfunding_platform.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CampaignDonorResponse(
        String donorName,
        boolean anonymous,
        BigDecimal amount,
        LocalDateTime donatedAt
) {}