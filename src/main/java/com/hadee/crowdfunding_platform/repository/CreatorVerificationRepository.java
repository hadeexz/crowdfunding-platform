package com.hadee.crowdfunding_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hadee.crowdfunding_platform.entity.CreatorVerification;

public interface CreatorVerificationRepository
        extends JpaRepository<CreatorVerification, Long> {

}
