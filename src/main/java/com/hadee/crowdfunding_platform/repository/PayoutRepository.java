package com.hadee.crowdfunding_platform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hadee.crowdfunding_platform.entity.Payout;
import com.hadee.crowdfunding_platform.entity.enums.PayoutStatus;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    Optional<Payout> findByCampaignId(Long campaignId);

    List<Payout> findByStatus(PayoutStatus status);
}