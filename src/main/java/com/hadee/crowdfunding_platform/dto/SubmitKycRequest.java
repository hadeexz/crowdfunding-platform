package com.hadee.crowdfunding_platform.dto;

import com.hadee.crowdfunding_platform.entity.enums.DocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitKycRequest(

        @NotNull(message = "Document type is required")
        DocumentType documentType,

        @NotBlank(message = "Last 4 characters of the document number are required")
        @Pattern(regexp = "^[A-Za-z0-9]{4}$",
                 message = "Send exactly the last 4 letters or digits of the document number")
        String documentNumberLast4,

        @NotBlank(message = "Document file link is required")
        @Size(max = 500, message = "Link must be at most 500 characters")
        @Pattern(regexp = "^https://\\S+$",
                 message = "Link must start with https:// and contain no spaces")
        String documentFileUrl
) {}