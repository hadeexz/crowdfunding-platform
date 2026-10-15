package com.hadee.crowdfunding_platform.dto;

import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.Role;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        AccountType accountType,
        Role role,
        VerificationStatus verificationStatus
) {}