package com.hadee.crowdfunding_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectKycRequest(

        @NotBlank(message = "Remarks are required when rejecting")
        @Size(max = 500, message = "Remarks must be at most 500 characters")
        String remarks
) {}