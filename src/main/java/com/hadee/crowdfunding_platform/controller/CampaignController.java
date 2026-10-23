package com.hadee.crowdfunding_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hadee.crowdfunding_platform.dto.CampaignResponse;
import com.hadee.crowdfunding_platform.dto.CreateCampaignRequest;
import com.hadee.crowdfunding_platform.entity.enums.Role;
import com.hadee.crowdfunding_platform.security.UserPrincipal;
import com.hadee.crowdfunding_platform.service.CampaignService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCampaignRequest request) {
        CampaignResponse response = campaignService.create(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Public page. principal is null when the visitor is not logged in.
    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long viewerId = (principal == null) ? null : principal.id();
        boolean viewerIsAdmin = principal != null && principal.role() == Role.ADMIN;

        return ResponseEntity.ok(campaignService.getById(id, viewerId, viewerIsAdmin));
    }
}