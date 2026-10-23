package com.hadee.crowdfunding_platform.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByCreatorId(Long creatorId);

    List<Campaign> findByStatus(CampaignStatus status);

    List<Campaign> findByStatusAndDeadlineBefore(CampaignStatus status, LocalDateTime time);

    @Query("select c from Campaign c join fetch c.creator where c.id = :id")
    Optional<Campaign> findByIdWithCreator(@Param("id") Long id);
}