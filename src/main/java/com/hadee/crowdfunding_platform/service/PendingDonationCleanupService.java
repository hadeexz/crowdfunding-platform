package com.hadee.crowdfunding_platform.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.config.GiveLivesProperties;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;
import com.hadee.crowdfunding_platform.repository.DonationRepository;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingDonationCleanupService {

    private final DonationRepository donationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final GiveLivesProperties props;

    /** Marks old, never-paid PENDING donations as FAILED. Returns how many were expired. */
    @Transactional
    public int expireStalePendingDonations() {

        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC)
                .minusMinutes(props.getPendingDonationExpiryMinutes());

        List<Donation> stale = donationRepository
                .findByStatusAndCreatedAtBefore(DonationStatus.PENDING, cutoff);

        int expired = 0;
        for (Donation donation : stale) {
            Long id = donation.getId();

            // Payment first: only if it is still unpaid (CREATED). If a payment is
            // arriving right now, this changes 0 rows and we leave the donation alone.
            if (paymentTransactionRepository.existsByDonationId(id)) {
                int changed = paymentTransactionRepository.changeStatusIfCurrent(
                        id, PaymentStatus.CREATED, PaymentStatus.FAILED,
                        "Payment not completed in time");
                if (changed == 0) {
                    continue;
                }
            }
            expired += donationRepository.changeStatusIfCurrent(
                    id, DonationStatus.PENDING, DonationStatus.FAILED);
        }

        if (expired > 0) {
            log.info("Expired {} stale pending donation(s)", expired);
        }
        return expired;
    }
}