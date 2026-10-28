package com.hadee.crowdfunding_platform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hadee.crowdfunding_platform.entity.PaymentTransaction;

import org.springframework.data.jpa.repository.EntityGraph;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentTransaction> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<PaymentTransaction> findByDonationId(Long donationId);

        // Loads donation and donor in the same query (no lazy-loading problems outside a transaction)
    @EntityGraph(attributePaths = {"donation", "donation.donor"})
    Optional<PaymentTransaction> findWithDonationByRazorpayOrderId(String razorpayOrderId);
}