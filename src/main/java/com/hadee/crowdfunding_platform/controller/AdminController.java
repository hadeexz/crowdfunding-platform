package com.hadee.crowdfunding_platform.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hadee.crowdfunding_platform.security.UserPrincipal;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    // Temporary test endpoint. Real admin APIs (KYC approval, bans, payout
    // review) will be added to this controller in later phases.
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of(
                "message", "Admin access granted",
                "adminId", principal.id(),
                "email", principal.email()));
    }
}