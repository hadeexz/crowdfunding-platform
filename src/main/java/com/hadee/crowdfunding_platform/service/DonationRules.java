package com.hadee.crowdfunding_platform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.hadee.crowdfunding_platform.config.GiveLivesProperties;
import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import com.hadee.crowdfunding_platform.exception.ConflictException;
import com.hadee.crowdfunding_platform.exception.ForbiddenActionException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DonationRules {

    private final GiveLivesProperties props;

    /** Result of splitting a donation into our fee and the creator's share. */
    public record FeeSplit(BigDecimal platformFee, BigDecimal netAmount) {}

    public void checkCanDonate(Campaign campaign, Long donorId, BigDecimal amount) {

        CampaignStatus status = campaign.getStatus();

        // hidden campaigns look "not found" (same idea as Phase 4)
        if (status == CampaignStatus.DRAFT || status == CampaignStatus.SUSPENDED) {
            throw new ResourceNotFoundException("Campaign not found");
        }
        if (status != CampaignStatus.ACTIVE) {
            throw new ConflictException("This campaign is not accepting donations");
        }
        if (!campaign.getDeadline().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ConflictException("This campaign has ended");
        }
        if (campaign.getCreator().getId().equals(donorId)) {
            throw new ForbiddenActionException("You cannot donate to your own campaign");
        }

        BigDecimal remaining = campaign.getGoalAmount().subtract(campaign.getCurrentAmount());
        boolean isFinalTopUp = amount.compareTo(remaining) == 0;

        if (amount.compareTo(props.getMinDonationAmount()) < 0 && !isFinalTopUp) {
            throw new IllegalArgumentException(
                    "Minimum donation is " + props.getMinDonationAmount().toPlainString());
        }
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "This campaign needs only " + remaining.toPlainString() + " more");
        }
    }

    public FeeSplit calculateFee(BigDecimal amount) {
        BigDecimal fee = amount
                .multiply(props.getPlatformFeePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = amount.subtract(fee).setScale(2, RoundingMode.HALF_UP);
        return new FeeSplit(fee, net);
    }
}