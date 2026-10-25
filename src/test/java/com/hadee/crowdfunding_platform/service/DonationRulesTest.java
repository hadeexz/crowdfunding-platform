package com.hadee.crowdfunding_platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hadee.crowdfunding_platform.config.GiveLivesProperties;
import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import com.hadee.crowdfunding_platform.exception.ConflictException;
import com.hadee.crowdfunding_platform.exception.ForbiddenActionException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;

class DonationRulesTest {

    private static final Long CREATOR_ID = 1L;
    private static final Long DONOR_ID = 2L;

    private DonationRules rules;

    @BeforeEach
    void setUp() {
        GiveLivesProperties props = mock(GiveLivesProperties.class);
        when(props.getPlatformFeePercent()).thenReturn(new BigDecimal("10"));
        when(props.getMinDonationAmount()).thenReturn(new BigDecimal("10"));
        rules = new DonationRules(props);
    }

    private Campaign campaign(CampaignStatus status, String goal, String current, int daysLeft) {
        User creator = new User();
        creator.setId(CREATOR_ID);

        Campaign c = new Campaign();
        c.setCreator(creator);
        c.setStatus(status);
        c.setGoalAmount(new BigDecimal(goal));
        c.setCurrentAmount(new BigDecimal(current));
        c.setDeadline(LocalDateTime.now(ZoneOffset.UTC).plusDays(daysLeft));
        return c;
    }

    @Test
    void fee_is_ten_percent() {
        DonationRules.FeeSplit split = rules.calculateFee(new BigDecimal("1000"));
        assertEquals(new BigDecimal("100.00"), split.platformFee());
        assertEquals(new BigDecimal("900.00"), split.netAmount());
    }

    @Test
    void fee_and_net_always_add_up_to_amount() {
        BigDecimal amount = new BigDecimal("10.05");
        DonationRules.FeeSplit split = rules.calculateFee(amount);
        assertEquals(new BigDecimal("1.01"), split.platformFee());
        assertEquals(0, amount.compareTo(split.platformFee().add(split.netAmount())));
    }

    @Test
    void valid_donation_is_accepted() {
        Campaign c = campaign(CampaignStatus.ACTIVE, "1000", "0", 10);
        assertDoesNotThrow(() -> rules.checkCanDonate(c, DONOR_ID, new BigDecimal("500")));
    }

    @Test
    void draft_campaign_looks_not_found() {
        Campaign c = campaign(CampaignStatus.DRAFT, "1000", "0", 10);
        assertThrows(ResourceNotFoundException.class,
                () -> rules.checkCanDonate(c, DONOR_ID, new BigDecimal("500")));
    }

    @Test
    void goal_reached_campaign_is_rejected() {
        Campaign c = campaign(CampaignStatus.GOAL_REACHED, "1000", "1000", 10);
        assertThrows(ConflictException.class,
                () -> rules.checkCanDonate(c, DONOR_ID, new BigDecimal("500")));
    }

    @Test
    void campaign_past_deadline_is_rejected() {
        Campaign c = campaign(CampaignStatus.ACTIVE, "1000", "0", -1);
        assertThrows(ConflictException.class,
                () -> rules.checkCanDonate(c, DONOR_ID, new BigDecimal("500")));
    }

    @Test
    void creator_cannot_donate_to_own_campaign() {
        Campaign c = campaign(CampaignStatus.ACTIVE, "1000", "0", 10);
        assertThrows(ForbiddenActionException.class,
                () -> rules.checkCanDonate(c, CREATOR_ID, new BigDecimal("500")));
    }

    @Test
    void amount_below_minimum_is_rejected() {
        Campaign c = campaign(CampaignStatus.ACTIVE, "1000", "0", 10);
        assertThrows(IllegalArgumentException.class,
                () -> rules.checkCanDonate(c, DONOR_ID, new BigDecimal("5")));
    }

    @Test
    void amount_above_remaining_is_rejected() {
        Campaign c = campaign(CampaignStatus.ACTIVE, "1000", "900", 10);
        assertThrows(IllegalArgumentException.class,
                () -> rules.checkCanDonate(c, DONOR_ID, new BigDecimal("200")));
    }

    @Test
    void final_small_top_up_below_minimum_is_allowed() {
        Campaign c = campaign(CampaignStatus.ACTIVE, "1000", "995", 10);
        assertDoesNotThrow(() -> rules.checkCanDonate(c, DONOR_ID, new BigDecimal("5")));
    }
}