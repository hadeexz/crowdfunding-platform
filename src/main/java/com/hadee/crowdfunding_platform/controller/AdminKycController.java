package com.hadee.crowdfunding_platform.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hadee.crowdfunding_platform.dto.AdminKycResponse;
import com.hadee.crowdfunding_platform.dto.RejectKycRequest;
import com.hadee.crowdfunding_platform.entity.enums.KycStatus;
import com.hadee.crowdfunding_platform.security.UserPrincipal;
import com.hadee.crowdfunding_platform.service.AdminKycService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/kyc")
public class AdminKycController {

    private final AdminKycService adminKycService;

    public AdminKycController(AdminKycService adminKycService) {
        this.adminKycService = adminKycService;
    }

    @GetMapping
    public ResponseEntity<List<AdminKycResponse>> list(
            @RequestParam(defaultValue = "PENDING") KycStatus status) {
        return ResponseEntity.ok(adminKycService.list(status));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminKycResponse> approve(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                adminKycService.approve(principal.id(), id, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminKycResponse> reject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RejectKycRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                adminKycService.reject(principal.id(), id, request.remarks(),
                        httpRequest.getRemoteAddr()));
    }
}