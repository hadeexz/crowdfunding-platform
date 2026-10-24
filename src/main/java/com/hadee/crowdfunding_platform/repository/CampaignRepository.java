package com.hadee.crowdfunding_platform.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;

public interface CampaignRepository
        extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {

    List<Campaign> findByCreatorId(Long creatorId);

    List<Campaign> findByStatus(CampaignStatus status);

    List<Campaign> findByStatusAndDeadlineBefore(CampaignStatus status, LocalDateTime time);

    @Query("select c from Campaign c join fetch c.creator where c.id = :id")
    Optional<Campaign> findByIdWithCreator(@Param("id") Long id);

    // Same method Spring already provides, but now it loads the creator with each campaign
    @Override
    @EntityGraph(attributePaths = "creator")
    Page<Campaign> findAll(Specification<Campaign> spec, Pageable pageable);
}