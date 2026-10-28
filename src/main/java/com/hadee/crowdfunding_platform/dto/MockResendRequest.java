package com.hadee.crowdfunding_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MockResendRequest(
        @NotBlank(message = "Event id is required")
        @Size(max = 100, message = "Event id is too long")
        String eventId
) {}