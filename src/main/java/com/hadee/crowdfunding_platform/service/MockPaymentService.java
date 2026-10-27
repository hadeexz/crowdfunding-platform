package com.hadee.crowdfunding_platform.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.dto.MockPayRequest;
import com.hadee.crowdfunding_platform.dto.MockPaymentResponse;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.PaymentTransaction;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;
import com.hadee.crowdfunding_platform.exception.ConflictException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.payment.MockGateway;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnProperty(name = "givelives.payment.provider", havingValue = "mock")
@RequiredArgsConstructor
public class MockPaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final MockGateway mockGateway;

    // Pretends to be the bank page: the donor chooses success or failure.
    @Transactional(readOnly = true)
    public MockPaymentResponse simulate(Long donorId, MockPayRequest request) {

        String orderId = request.razorpayOrderId();
        if (!orderId.startsWith(MockGateway.ORDER_PREFIX)) {
            throw new IllegalArgumentException("Not a mock order");
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        Donation donation = transaction.getDonation();
        if (!donation.getDonor().getId().equals(donorId)) {
            throw new ResourceNotFoundException("Payment not found");
        }
        if (transaction.getStatus() != PaymentStatus.CREATED) {
            throw new ConflictException("This payment was already processed");
        }

        if (request.outcome() == MockPayRequest.Outcome.FAILURE) {
            return new MockPaymentResponse(orderId, null, null, "FAILURE",
                    "Simulated payment failure");
        }

        String paymentId = mockGateway.newPaymentId();
        String signature = mockGateway.sign(orderId, paymentId);
        return new MockPaymentResponse(orderId, paymentId, signature, "SUCCESS",
                "Simulated payment success");
    }
}