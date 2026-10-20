package com.hadee.crowdfunding_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hadee.crowdfunding_platform.dto.KycResponse;
import com.hadee.crowdfunding_platform.dto.SubmitKycRequest;
import com.hadee.crowdfunding_platform.security.UserPrincipal;
import com.hadee.crowdfunding_platform.service.KycService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping
    public ResponseEntity<KycResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
                                              @Valid @RequestBody SubmitKycRequest request) {
        kycService.submit(principal.id(), request);
        // submitted_at is filled by the database, so read the saved row back
        KycResponse response = kycService.getMyLatest(principal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<KycResponse> myLatest(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(kycService.getMyLatest(principal.id()));
    }
}