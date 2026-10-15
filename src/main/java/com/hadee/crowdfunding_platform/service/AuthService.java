package com.hadee.crowdfunding_platform.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.dto.RegisterRequest;
import com.hadee.crowdfunding_platform.dto.UserResponse;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.Role;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;
import com.hadee.crowdfunding_platform.exception.EmailAlreadyExistsException;
import com.hadee.crowdfunding_platform.repository.UserRepository;


@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        
        // Public signup is only for donor, creator, organization (never admin)
        if ("ADMIN".equals(request.accountType().name())) {
            throw new IllegalArgumentException("This account type cannot be registered publicly");
        }

        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAccountType(request.accountType());
        user.setRole(Role.USER);

        // Donors need no KYC. Creators and organizations must verify later.
        if (request.accountType() == AccountType.DONOR) {
            user.setVerificationStatus(VerificationStatus.NOT_REQUIRED);
        } else {
            user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        }

        User saved = userRepository.save(user);

        return new UserResponse(
                saved.getId(),
                saved.getFullName(),
                saved.getEmail(),
                saved.getAccountType(),
                saved.getRole(),
                saved.getVerificationStatus());
    }
}