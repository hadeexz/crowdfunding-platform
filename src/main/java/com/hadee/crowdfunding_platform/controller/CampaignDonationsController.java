package com.hadee.crowdfunding_platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hadee.crowdfunding_platform.dto.CampaignDonorResponse;
import com.hadee.crowdfunding_platform.dto.PageResponse;
import com.hadee.crowdfunding_platform.entity.enums.Role;
import com.hadee.crowdfunding_platform.security.UserPrincipal;
import com.hadee.crowdfunding_platform.service.DonationQueryService;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/donations")
public class CampaignDonationsController {

    private final DonationQueryService donationQueryService;

    public CampaignDonationsController(DonationQueryService donationQueryService) {
        this.donationQueryService = donationQueryService;
    }

    // Public. principal is null when the visitor is not logged in.
    @GetMapping
    public ResponseEntity<PageResponse<CampaignDonorResponse>> supporters(
            @PathVariable Long campaignId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Long viewerId = (principal == null) ? null : principal.id();
        boolean viewerIsAdmin = principal != null && principal.role() == Role.ADMIN;

        return ResponseEntity.ok(PageResponse.from(
                donationQueryService.listForCampaign(campaignId, viewerId, viewerIsAdmin, page, size)));
    }
}