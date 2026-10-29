package com.hadee.crowdfunding_platform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hadee.crowdfunding_platform.entity.PaymentTransaction;

import org.springframework.data.jpa.repository.EntityGraph;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

        // One query for a whole page of donations
    List<PaymentTransaction> findByDonationIdIn(Collection<Long> donationIds);

    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentTransaction> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<PaymentTransaction> findByDonationId(Long donationId);

        boolean existsByDonationId(Long donationId);

    @Modifying
    @Query("update PaymentTransaction p set p.status = :to, p.failureReason = :reason "
            + "where p.donation.id = :donationId and p.status = :from")
    int changeStatusIfCurrent(@Param("donationId") Long donationId,
                              @Param("from") PaymentStatus from,
                              @Param("to") PaymentStatus to,
                              @Param("reason") String reason);

        // Loads donation and donor in the same query (no lazy-loading problems outside a transaction)
    @EntityGraph(attributePaths = {"donation", "donation.donor"})
    Optional<PaymentTransaction> findWithDonationByRazorpayOrderId(String razorpayOrderId);
}