package com.hadee.crowdfunding_platform.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByDonorId(Long donorId);

    List<Donation> findByCampaignIdAndStatus(Long campaignId, DonationStatus status);

    // used later by the scheduled job that cleans up old PENDING donations
    List<Donation> findByStatusAndCreatedAtBefore(DonationStatus status, LocalDateTime time);

    // "Has anyone really donated to this campaign?" (used by the edit rules)
    boolean existsByCampaignIdAndStatusIn(Long campaignId, Collection<DonationStatus> statuses);

        // Changes the status ONLY if it still has the expected value (returns rows changed: 0 or 1)
    @Modifying
    @Query("update Donation d set d.status = :to where d.id = :id and d.status = :from")
    int changeStatusIfCurrent(@Param("id") Long id,
                              @Param("from") DonationStatus from,
                              @Param("to") DonationStatus to);

                                  // "My donations": the campaign is loaded together with the page (no extra query per row)
    @EntityGraph(attributePaths = "campaign")
    Page<Donation> findPageByDonorId(Long donorId, Pageable pageable);

    @EntityGraph(attributePaths = "campaign")
    Page<Donation> findPageByDonorIdAndStatus(Long donorId, DonationStatus status, Pageable pageable);

    // Campaign supporters: the donor is loaded together with the page
    @EntityGraph(attributePaths = "donor")
    Page<Donation> findPageByCampaignIdAndStatus(Long campaignId, DonationStatus status, Pageable pageable);
}