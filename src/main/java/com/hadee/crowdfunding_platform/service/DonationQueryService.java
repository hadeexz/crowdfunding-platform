package com.hadee.crowdfunding_platform.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.dto.CampaignDonorResponse;
import com.hadee.crowdfunding_platform.dto.MyDonationResponse;
import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.PaymentTransaction;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.repository.CampaignRepository;
import com.hadee.crowdfunding_platform.repository.DonationRepository;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DonationQueryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String ANONYMOUS_NAME = "Anonymous";

    private final DonationRepository donationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CampaignRepository campaignRepository;

    /** The logged-in donor's own donations, newest first. The donor id comes from the token. */
    @Transactional(readOnly = true)
    public Page<MyDonationResponse> listMine(Long donorId, DonationStatus status, int page, int size) {

        Pageable pageable = pageable(page, size, "createdAt");

        Page<Donation> donations = (status == null)
                ? donationRepository.findPageByDonorId(donorId, pageable)
                : donationRepository.findPageByDonorIdAndStatus(donorId, status, pageable);

        // payment status for the whole page in ONE query
        List<Long> ids = donations.getContent().stream().map(Donation::getId).toList();
        Map<Long, PaymentStatus> paymentStatuses = new HashMap<>();
        if (!ids.isEmpty()) {
            for (PaymentTransaction tx : paymentTransactionRepository.findByDonationIdIn(ids)) {
                paymentStatuses.put(tx.getDonation().getId(), tx.getStatus());
            }
        }

        return donations.map(d -> toMine(d, paymentStatuses.get(d.getId())));
    }

    /** Public supporters list: only COMPLETED donations, anonymous donors stay hidden. */
    @Transactional(readOnly = true)
    public Page<CampaignDonorResponse> listForCampaign(Long campaignId, Long viewerId,
                                                       boolean viewerIsAdmin, int page, int size) {

        Campaign campaign = campaignRepository.findByIdWithCreator(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));

        // hidden campaigns look "not found" (same rule as the campaign page)
        boolean hidden = campaign.getStatus() == CampaignStatus.DRAFT
                || campaign.getStatus() == CampaignStatus.SUSPENDED;
        boolean isOwner = viewerId != null && viewerId.equals(campaign.getCreator().getId());
        if (hidden && !isOwner && !viewerIsAdmin) {
            throw new ResourceNotFoundException("Campaign not found");
        }

        Pageable pageable = pageable(page, size, "completedAt");
        return donationRepository
                .findPageByCampaignIdAndStatus(campaignId, DonationStatus.COMPLETED, pageable)
                .map(this::toDonor);
    }

    private MyDonationResponse toMine(Donation d, PaymentStatus paymentStatus) {
        return new MyDonationResponse(
                d.getId(),
                d.getCampaign().getId(),
                d.getCampaign().getTitle(),
                d.getAmount(),
                d.isAnonymous(),
                d.getStatus(),
                paymentStatus,
                noteFor(paymentStatus),
                d.getCreatedAt(),
                d.getCompletedAt());
    }

    private CampaignDonorResponse toDonor(Donation d) {
        String name = d.isAnonymous() ? ANONYMOUS_NAME : d.getDonor().getFullName();
        return new CampaignDonorResponse(name, d.isAnonymous(), d.getAmount(), d.getCompletedAt());
    }

    private String noteFor(PaymentStatus paymentStatus) {
        if (paymentStatus == PaymentStatus.REFUND_INITIATED) {
            return "Your payment was received but could not be added to the campaign. A refund will be sent to you.";
        }
        if (paymentStatus == PaymentStatus.REFUNDED) {
            return "This payment has been refunded.";
        }
        return null;
    }

    // newest first; "id" is the tiebreaker so rows never repeat or vanish between pages
    private Pageable pageable(int page, int size, String sortProperty) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, sortProperty).and(Sort.by(Sort.Direction.DESC, "id")));
    }
}