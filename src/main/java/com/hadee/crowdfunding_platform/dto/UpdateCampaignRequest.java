package com.hadee.crowdfunding_platform.dto;

import java.math.BigDecimal;

import com.hadee.crowdfunding_platform.entity.enums.CampaignCategory;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCampaignRequest(

        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Size(min = 20, max = 10000, message = "Description must be 20 to 10000 characters")
        String description,

        @Size(max = 500, message = "Image link must be at most 500 characters")
        @Pattern(regexp = "^https://\\S+$",
                 message = "Image link must start with https:// and contain no spaces")
        String imageUrl,

        CampaignCategory category,

        @Digits(integer = 10, fraction = 2,
                message = "Goal amount can have at most 10 digits and 2 decimals")
        BigDecimal goalAmount,

        Integer durationDays
) {}