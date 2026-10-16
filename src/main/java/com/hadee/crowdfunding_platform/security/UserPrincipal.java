package com.hadee.crowdfunding_platform.security;

import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.Role;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;

public record UserPrincipal(
        Long id,
        String fullName,
        String email,
        AccountType accountType,
        Role role,
        VerificationStatus verificationStatus
) {}