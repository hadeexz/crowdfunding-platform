package com.hadee.crowdfunding_platform.config;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
@Getter
public class GiveLivesProperties {

    @Value("${givelives.platform-fee-percent}")
    private BigDecimal platformFeePercent;

    @Value("${givelives.min-campaign-goal}")
    private BigDecimal minCampaignGoal;

    @Value("${givelives.payout-review-threshold}")
    private BigDecimal payoutReviewThreshold;

    @Value("${givelives.campaign-min-days}")
    private int campaignMinDays;

    @Value("${givelives.campaign-max-days}")
    private int campaignMaxDays;
}