package com.hadee.crowdfunding_platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.PaymentTransaction;
import com.hadee.crowdfunding_platform.entity.WebhookEvent;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;
import com.hadee.crowdfunding_platform.entity.enums.WebhookProcessingStatus;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;
import com.hadee.crowdfunding_platform.repository.WebhookEventRepository;

class PaymentEventProcessorTest {

    private WebhookEventRepository eventRepo;
    private PaymentTransactionRepository txRepo;
    private PaymentEventProcessor processor;

    @BeforeEach
    void setUp() {
        eventRepo = mock(WebhookEventRepository.class);
        txRepo = mock(PaymentTransactionRepository.class);
        processor = new PaymentEventProcessor(eventRepo, txRepo);
    }

    // a campaign + donation + transaction, ready to be paid
    private PaymentTransaction scenario(String goal, String current, CampaignStatus status,
                                        int daysLeft, String amount) {
        Campaign campaign = new Campaign();
        campaign.setGoalAmount(new BigDecimal(goal));
        campaign.setCurrentAmount(new BigDecimal(current));
        campaign.setStatus(status);
        campaign.setDeadline(LocalDateTime.now(ZoneOffset.UTC).plusDays(daysLeft));

        Donation donation = new Donation();
        donation.setCampaign(campaign);
        donation.setAmount(new BigDecimal(amount));

        PaymentTransaction tx = new PaymentTransaction();
        tx.setDonation(donation);
        tx.setRazorpayOrderId("order_1");
        tx.setAmount(new BigDecimal(amount));

        when(txRepo.findWithDonationByRazorpayOrderId("order_1")).thenReturn(Optional.of(tx));
        return tx;
    }

    private WebhookEvent event(String type, long paise) {
        JSONObject entity = new JSONObject()
                .put("id", "pay_1")
                .put("order_id", "order_1")
                .put("amount", paise)
                .put("error_description", "Bank declined");
        JSONObject json = new JSONObject()
                .put("event", type)
                .put("payload", new JSONObject().put("payment", new JSONObject().put("entity", entity)));

        WebhookEvent e = new WebhookEvent();
        e.setId(1L);
        e.setEventType(type);
        e.setPayload(json.toString());
        when(eventRepo.findById(1L)).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void successful_payment_completes_donation_and_raises_campaign_total() {
        PaymentTransaction tx = scenario("1000", "0", CampaignStatus.ACTIVE, 10, "600");
        WebhookEvent e = event("payment.captured", 60000);

        processor.process(1L);

        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertEquals("pay_1", tx.getRazorpayPaymentId());
        assertNotNull(tx.getPaidAt());
        assertEquals(DonationStatus.COMPLETED, tx.getDonation().getStatus());
        assertNotNull(tx.getDonation().getCompletedAt());
        Campaign c = tx.getDonation().getCampaign();
        assertEquals(0, new BigDecimal("600").compareTo(c.getCurrentAmount()));
        assertEquals(CampaignStatus.ACTIVE, c.getStatus());
        assertEquals(WebhookProcessingStatus.PROCESSED, e.getProcessingStatus());
        assertNotNull(e.getProcessedAt());
        assertEquals(1, e.getAttemptCount());
    }

    @Test
    void payment_that_reaches_the_goal_sets_goal_reached() {
        PaymentTransaction tx = scenario("1000", "600", CampaignStatus.ACTIVE, 10, "400");
        event("payment.captured", 40000);

        processor.process(1L);

        Campaign c = tx.getDonation().getCampaign();
        assertEquals(0, new BigDecimal("1000").compareTo(c.getCurrentAmount()));
        assertEquals(CampaignStatus.GOAL_REACHED, c.getStatus());
        assertEquals(DonationStatus.COMPLETED, tx.getDonation().getStatus());
    }

    @Test
    void payment_that_would_overshoot_the_goal_is_marked_refund_due() {
        PaymentTransaction tx = scenario("1000", "800", CampaignStatus.ACTIVE, 10, "400");
        event("payment.captured", 40000);

        processor.process(1L);

        assertEquals(PaymentStatus.REFUND_INITIATED, tx.getStatus());
        assertEquals(0, new BigDecimal("400").compareTo(tx.getRefundAmount()));
        assertEquals(DonationStatus.FAILED, tx.getDonation().getStatus());
        // the campaign total must NOT change
        assertEquals(0, new BigDecimal("800").compareTo(tx.getDonation().getCampaign().getCurrentAmount()));
        assertEquals(CampaignStatus.ACTIVE, tx.getDonation().getCampaign().getStatus());
    }

    @Test
    void payment_to_a_campaign_that_is_not_active_is_marked_refund_due() {
        PaymentTransaction tx = scenario("1000", "1000", CampaignStatus.GOAL_REACHED, 10, "100");
        event("payment.captured", 10000);

        processor.process(1L);

        assertEquals(PaymentStatus.REFUND_INITIATED, tx.getStatus());
        assertEquals(DonationStatus.FAILED, tx.getDonation().getStatus());
    }

    @Test
    void payment_after_the_deadline_is_marked_refund_due() {
        PaymentTransaction tx = scenario("1000", "0", CampaignStatus.ACTIVE, -1, "100");
        event("payment.captured", 10000);

        processor.process(1L);

        assertEquals(PaymentStatus.REFUND_INITIATED, tx.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(tx.getDonation().getCampaign().getCurrentAmount()));
    }

    @Test
    void already_confirmed_payment_is_not_counted_twice() {
        PaymentTransaction tx = scenario("1000", "600", CampaignStatus.ACTIVE, 10, "600");
        tx.setStatus(PaymentStatus.SUCCESS);
        WebhookEvent e = event("payment.captured", 60000);

        processor.process(1L);

        assertEquals(0, new BigDecimal("600").compareTo(tx.getDonation().getCampaign().getCurrentAmount()));
        assertEquals(WebhookProcessingStatus.PROCESSED, e.getProcessingStatus());
    }

    @Test
    void wrong_amount_is_rejected_and_nothing_is_confirmed() {
        PaymentTransaction tx = scenario("1000", "0", CampaignStatus.ACTIVE, 10, "600");
        event("payment.captured", 100);

        assertThrows(IllegalStateException.class, () -> processor.process(1L));
        assertEquals(PaymentStatus.CREATED, tx.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(tx.getDonation().getCampaign().getCurrentAmount()));
    }

    @Test
    void failed_payment_marks_transaction_and_donation_failed() {
        PaymentTransaction tx = scenario("1000", "0", CampaignStatus.ACTIVE, 10, "600");
        WebhookEvent e = event("payment.failed", 60000);

        processor.process(1L);

        assertEquals(PaymentStatus.FAILED, tx.getStatus());
        assertEquals("Bank declined", tx.getFailureReason());
        assertEquals(DonationStatus.FAILED, tx.getDonation().getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(tx.getDonation().getCampaign().getCurrentAmount()));
        assertEquals(WebhookProcessingStatus.PROCESSED, e.getProcessingStatus());
    }

    @Test
    void late_failed_message_does_not_undo_a_successful_payment() {
        PaymentTransaction tx = scenario("1000", "600", CampaignStatus.ACTIVE, 10, "600");
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.getDonation().setStatus(DonationStatus.COMPLETED);
        event("payment.failed", 60000);

        processor.process(1L);

        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertEquals(DonationStatus.COMPLETED, tx.getDonation().getStatus());
    }

    @Test
    void unknown_order_is_ignored() {
        when(txRepo.findWithDonationByRazorpayOrderId("order_1")).thenReturn(Optional.empty());
        WebhookEvent e = event("payment.captured", 10000);

        processor.process(1L);

        assertEquals(WebhookProcessingStatus.IGNORED, e.getProcessingStatus());
    }

    @Test
    void other_event_types_are_ignored() {
        WebhookEvent e = event("order.paid", 10000);

        processor.process(1L);

        assertEquals(WebhookProcessingStatus.IGNORED, e.getProcessingStatus());
    }

        @Test
    void payment_after_the_pending_donation_expired_still_completes() {
        PaymentTransaction tx = scenario("1000", "0", CampaignStatus.ACTIVE, 10, "300");
        tx.setStatus(PaymentStatus.FAILED);
        tx.setFailureReason("Payment not completed in time");
        tx.getDonation().setStatus(DonationStatus.FAILED);
        event("payment.captured", 30000);

        processor.process(1L);

        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertNull(tx.getFailureReason());
        assertEquals(DonationStatus.COMPLETED, tx.getDonation().getStatus());
        assertEquals(0, new BigDecimal("300").compareTo(tx.getDonation().getCampaign().getCurrentAmount()));
    }
}