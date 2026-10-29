package com.hadee.crowdfunding_platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hadee.crowdfunding_platform.config.GiveLivesProperties;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;
import com.hadee.crowdfunding_platform.repository.DonationRepository;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;

class PendingDonationCleanupServiceTest {

    private DonationRepository donationRepo;
    private PaymentTransactionRepository txRepo;
    private PendingDonationCleanupService service;

    @BeforeEach
    void setUp() {
        donationRepo = mock(DonationRepository.class);
        txRepo = mock(PaymentTransactionRepository.class);
        GiveLivesProperties props = mock(GiveLivesProperties.class);
        when(props.getPendingDonationExpiryMinutes()).thenReturn(30);
        service = new PendingDonationCleanupService(donationRepo, txRepo, props);
    }

    private void oneStaleDonation(Long id) {
        Donation d = new Donation();
        d.setId(id);
        when(donationRepo.findByStatusAndCreatedAtBefore(eq(DonationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(d));
    }

    @Test
    void stale_donation_with_unpaid_transaction_is_expired() {
        oneStaleDonation(1L);
        when(txRepo.existsByDonationId(1L)).thenReturn(true);
        when(txRepo.changeStatusIfCurrent(1L, PaymentStatus.CREATED, PaymentStatus.FAILED,
                "Payment not completed in time")).thenReturn(1);
        when(donationRepo.changeStatusIfCurrent(1L, DonationStatus.PENDING, DonationStatus.FAILED))
                .thenReturn(1);

        assertEquals(1, service.expireStalePendingDonations());
    }

    @Test
    void donation_whose_payment_already_moved_on_is_left_alone() {
        oneStaleDonation(1L);
        when(txRepo.existsByDonationId(1L)).thenReturn(true);
        when(txRepo.changeStatusIfCurrent(any(), any(), any(), any())).thenReturn(0);

        assertEquals(0, service.expireStalePendingDonations());
        verify(donationRepo, never()).changeStatusIfCurrent(any(), any(), any());
    }

    @Test
    void stale_donation_without_a_transaction_is_expired() {
        oneStaleDonation(1L);
        when(txRepo.existsByDonationId(1L)).thenReturn(false);
        when(donationRepo.changeStatusIfCurrent(1L, DonationStatus.PENDING, DonationStatus.FAILED))
                .thenReturn(1);

        assertEquals(1, service.expireStalePendingDonations());
        verify(txRepo, never()).changeStatusIfCurrent(any(), any(), any(), any());
    }

    @Test
    void nothing_stale_does_nothing() {
        assertEquals(0, service.expireStalePendingDonations());
        verify(donationRepo, never()).changeStatusIfCurrent(any(), any(), any());
    }

    @Test
    void cutoff_is_the_configured_minutes_ago() {
        service.expireStalePendingDonations();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(donationRepo).findByStatusAndCreatedAtBefore(eq(DonationStatus.PENDING), captor.capture());

        LocalDateTime expected = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30);
        assertTrue(Math.abs(Duration.between(captor.getValue(), expected).toSeconds()) < 5);
    }
}