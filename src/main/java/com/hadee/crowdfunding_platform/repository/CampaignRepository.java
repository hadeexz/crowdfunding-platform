package com.hadee.crowdfunding_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.hadee.crowdfunding_platform.entity.Campaign;

import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import java.time.LocalDateTime;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByCreatorId(Long creatorId);

    List<Campaign> findByStatus(CampaignStatus status);

    List<Campaign> findByStatusAndDeadlineBefore(CampaignStatus status, LocalDateTime time);
}