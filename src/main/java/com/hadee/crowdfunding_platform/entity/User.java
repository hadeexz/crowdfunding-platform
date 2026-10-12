package com.hadee.crowdfunding_platform.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.Role;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Nationalized
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Nationalized
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    @Column(name = "is_banned", nullable = false)
    private boolean banned;

    @Nationalized
    @Column(name = "ban_reason", length = 500)
    private String banReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

}
