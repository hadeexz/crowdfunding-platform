package com.hadee.crowdfunding_platform.dto;

import java.time.LocalDateTime;

import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.DocumentType;
import com.hadee.crowdfunding_platform.entity.enums.KycStatus;

public record AdminKycResponse(
        Long id,
        Long userId,
        String userFullName,
        String userEmail,
        AccountType accountType,
        DocumentType documentType,
        String documentNumberMasked,
        String documentFileUrl,
        KycStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String remarks
) {}