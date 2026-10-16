package com.hadee.crowdfunding_platform.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.dto.LoginRequest;
import com.hadee.crowdfunding_platform.dto.LoginResponse;
import com.hadee.crowdfunding_platform.dto.RegisterRequest;
import com.hadee.crowdfunding_platform.dto.UserResponse;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.Role;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;
import com.hadee.crowdfunding_platform.exception.AccountBannedException;
import com.hadee.crowdfunding_platform.exception.EmailAlreadyExistsException;
import com.hadee.crowdfunding_platform.exception.InvalidCredentialsException;
import com.hadee.crowdfunding_platform.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        // Same message for "no such email" and "wrong password"
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Ban is checked only AFTER the password is proven correct
        if (user.isBanned()) {
            throw new AccountBannedException("This account has been suspended");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                toResponse(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getAccountType(),
                user.getRole(),
                user.getVerificationStatus());
    }
}