package com.hadee.crowdfunding_platform.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.PaymentTransaction;
import com.hadee.crowdfunding_platform.entity.WebhookEvent;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;
import com.hadee.crowdfunding_platform.entity.enums.WebhookProcessingStatus;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;
import com.hadee.crowdfunding_platform.repository.WebhookEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentEventProcessor {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    // One transaction: the payment, the donation, the campaign and the event status
    // are saved together or not at all. Entities loaded here are saved automatically
    // when the method ends (JPA dirty checking). If two payments change the same
    // campaign at once, @Version makes one of them fail and be retried.
    @Transactional
    public void process(Long eventId) {

        WebhookEvent event = webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook event not found"));

        if (event.getProcessingStatus() == WebhookProcessingStatus.PROCESSED
                || event.getProcessingStatus() == WebhookProcessingStatus.IGNORED) {
            return;
        }

        event.setAttemptCount(event.getAttemptCount() + 1);
        JSONObject root = new JSONObject(event.getPayload());

        switch (event.getEventType()) {
            case "payment.captured" -> handleCaptured(event, paymentEntity(root));
            case "payment.failed" -> handleFailed(event, paymentEntity(root));
            default -> markIgnored(event, "Event type not handled: " + event.getEventType());
        }
    }

    // Called after process() rolled back, in its own small transaction.
    @Transactional
    public void markFailed(Long eventId, String error) {
        webhookEventRepository.findById(eventId).ifPresent(event -> {
            String message = (error == null || error.isBlank()) ? "Processing failed" : error;
            if (message.length() > 1000) {
                message = message.substring(0, 1000);
            }
            event.setProcessingStatus(WebhookProcessingStatus.FAILED);
            event.setErrorMessage(message);
            event.setAttemptCount(event.getAttemptCount() + 1);
        });
    }

    private void handleCaptured(WebhookEvent event, JSONObject payment) {

        String orderId = payment.getString("order_id");
        String paymentId = payment.getString("id");
        long paidPaise = payment.getLong("amount");

        PaymentTransaction tx = paymentTransactionRepository
                .findWithDonationByRazorpayOrderId(orderId).orElse(null);
        if (tx == null) {
            markIgnored(event, "Unknown order: " + orderId);
            return;
        }

        // Already confirmed (or refunded) earlier: never count the money twice
        PaymentStatus current = tx.getStatus();
        if (current == PaymentStatus.SUCCESS
                || current == PaymentStatus.REFUND_INITIATED
                || current == PaymentStatus.REFUNDED) {
            markProcessed(event);
            return;
        }

        // The amount paid must be exactly what we asked for
        long expectedPaise = tx.getAmount().movePointRight(2).longValueExact();
        if (paidPaise != expectedPaise) {
            throw new IllegalStateException("Amount mismatch for order " + orderId
                    + ": paid " + paidPaise + " paise, expected " + expectedPaise);
        }

        Donation donation = tx.getDonation();
        Campaign campaign = donation.getCampaign();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        BigDecimal newTotal = campaign.getCurrentAmount().add(tx.getAmount());

        boolean campaignCanAccept = campaign.getStatus() == CampaignStatus.ACTIVE
                && campaign.getDeadline().isAfter(now)
                && newTotal.compareTo(campaign.getGoalAmount()) <= 0;

        tx.setRazorpayPaymentId(paymentId);
        tx.setPaidAt(now);

        if (campaignCanAccept) {
            tx.setStatus(PaymentStatus.SUCCESS);
            tx.setFailureReason(null);

            donation.setStatus(DonationStatus.COMPLETED);
            donation.setCompletedAt(now);

            campaign.setCurrentAmount(newTotal);
            if (newTotal.compareTo(campaign.getGoalAmount()) == 0) {
                campaign.setStatus(CampaignStatus.GOAL_REACHED);
            }
        } else {
            // The money arrived but the campaign cannot take it: a refund is owed.
            // The campaign total is NOT changed.
            tx.setStatus(PaymentStatus.REFUND_INITIATED);
            tx.setRefundAmount(tx.getAmount());
            tx.setFailureReason("Campaign could not accept this payment. Refund due.");

            donation.setStatus(DonationStatus.FAILED);
            donation.setCompletedAt(null);
        }

        markProcessed(event);
    }

    private void handleFailed(WebhookEvent event, JSONObject payment) {

        String orderId = payment.getString("order_id");

        PaymentTransaction tx = paymentTransactionRepository
                .findWithDonationByRazorpayOrderId(orderId).orElse(null);
        if (tx == null) {
            markIgnored(event, "Unknown order: " + orderId);
            return;
        }

        // A late "failed" message must never undo a payment that already succeeded
        if (tx.getStatus() != PaymentStatus.CREATED) {
            markProcessed(event);
            return;
        }

        String reason = payment.isNull("error_description") ? "" : payment.optString("error_description", "");
        if (reason.isBlank()) {
            reason = "Payment failed";
        }
        if (reason.length() > 500) {
            reason = reason.substring(0, 500);
        }

        tx.setStatus(PaymentStatus.FAILED);
        tx.setFailureReason(reason);

        Donation donation = tx.getDonation();
        if (donation.getStatus() == DonationStatus.PENDING) {
            donation.setStatus(DonationStatus.FAILED);
        }

        markProcessed(event);
    }

    private JSONObject paymentEntity(JSONObject root) {
        return root.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
    }

    private void markProcessed(WebhookEvent event) {
        event.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
        event.setErrorMessage(null);
        event.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
    }

    private void markIgnored(WebhookEvent event, String note) {
        event.setProcessingStatus(WebhookProcessingStatus.IGNORED);
        event.setErrorMessage(note);
        event.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
    }
}