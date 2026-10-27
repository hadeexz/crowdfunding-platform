package com.hadee.crowdfunding_platform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.dto.CreateDonationRequest;
import com.hadee.crowdfunding_platform.dto.DonationOrderResponse;
import com.hadee.crowdfunding_platform.dto.PaymentVerificationResponse;
import com.hadee.crowdfunding_platform.dto.VerifyPaymentRequest;
import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.PaymentTransaction;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.exception.ForbiddenActionException;
import com.hadee.crowdfunding_platform.exception.PaymentGatewayException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.payment.PaymentGateway;
import com.hadee.crowdfunding_platform.repository.CampaignRepository;
import com.hadee.crowdfunding_platform.repository.DonationRepository;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;
import com.hadee.crowdfunding_platform.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DonationService {

    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final DonationRepository donationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final DonationRules rules;
    private final PaymentGateway paymentGateway;

    // NOT @Transactional on purpose: we do not keep a database transaction
    // open while waiting for the payment gateway over the internet.
    public DonationOrderResponse createDonation(Long donorId, CreateDonationRequest request) {

        User donor = userRepository.findById(donorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (donor.isBanned()) {
            throw new ForbiddenActionException("Your account is banned");
        }
        if (donor.getAccountType() == AccountType.ADMIN) {
            throw new ForbiddenActionException("Admin accounts cannot donate");
        }

        Campaign campaign = campaignRepository.findByIdWithCreator(request.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));

        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        rules.checkCanDonate(campaign, donorId, amount);

        // 1) Save our own record first (status PENDING)
        Donation donation = new Donation();
        donation.setCampaign(campaign);
        donation.setDonor(donor);
        donation.setAmount(amount);
        donation.setAnonymous(request.anonymous());
        donation = donationRepository.save(donation);

        // 2) Ask the payment gateway for an order
        String orderId;
        try {
            orderId = paymentGateway.createOrder(donation.getId(), campaign.getId(), amount);
        } catch (RuntimeException e) {
            log.error("Order creation failed for donation {}", donation.getId(), e);
            donation.setStatus(DonationStatus.FAILED);
            donationRepository.save(donation);
            throw new PaymentGatewayException("Could not start the payment. Please try again.");
        }

        // 3) Save the payment transaction with our fee
        DonationRules.FeeSplit split = rules.calculateFee(amount);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setDonation(donation);
        transaction.setRazorpayOrderId(orderId);
        transaction.setAmount(amount);
        transaction.setPlatformFee(split.platformFee());
        transaction.setNetAmount(split.netAmount());
        paymentTransactionRepository.save(transaction);

        return new DonationOrderResponse(
                donation.getId(),
                campaign.getId(),
                amount,
                split.platformFee(),
                split.netAmount(),
                "INR",
                orderId,
                paymentGateway.publicKey(),
                paymentGateway.provider(),
                donation.getStatus());
    }

    // Read-only check. The money is confirmed later by the webhook (Part 6).
    @Transactional(readOnly = true)
    public PaymentVerificationResponse verifyPayment(Long donorId, VerifyPaymentRequest request) {

        PaymentTransaction transaction = paymentTransactionRepository
                .findByRazorpayOrderId(request.razorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        Donation donation = transaction.getDonation();

        // someone else's order looks "not found", we do not confirm it exists
        if (!donation.getDonor().getId().equals(donorId)) {
            throw new ResourceNotFoundException("Payment not found");
        }

        boolean valid = paymentGateway.verifyPaymentSignature(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature());

        if (!valid) {
            log.warn("Invalid payment signature for donation {}", donation.getId());
            throw new IllegalArgumentException("Invalid payment signature");
        }

        String message = (donation.getStatus() == DonationStatus.COMPLETED)
                ? "Thank you! Your donation is confirmed."
                : "Payment verified. Your donation will be confirmed shortly.";

        return new PaymentVerificationResponse(donation.getId(), true, donation.getStatus(), message);
    }
}