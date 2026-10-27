package com.hadee.crowdfunding_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hadee.crowdfunding_platform.dto.CreateDonationRequest;
import com.hadee.crowdfunding_platform.dto.DonationOrderResponse;
import com.hadee.crowdfunding_platform.security.UserPrincipal;
import com.hadee.crowdfunding_platform.service.DonationService;

import com.hadee.crowdfunding_platform.dto.PaymentVerificationResponse;
import com.hadee.crowdfunding_platform.dto.VerifyPaymentRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/donations")
public class DonationController {

        @PostMapping("/verify")
    public ResponseEntity<PaymentVerificationResponse> verify(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok(donationService.verifyPayment(principal.id(), request));
    }

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;

        
    }

    @PostMapping
    public ResponseEntity<DonationOrderResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateDonationRequest request) {
        DonationOrderResponse response = donationService.createDonation(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}