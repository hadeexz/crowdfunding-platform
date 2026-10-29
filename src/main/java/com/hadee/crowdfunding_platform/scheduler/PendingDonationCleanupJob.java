package com.hadee.crowdfunding_platform.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hadee.crowdfunding_platform.service.PendingDonationCleanupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingDonationCleanupJob {

    private final PendingDonationCleanupService cleanupService;

    // fixedDelay: the next run starts N ms AFTER the previous one finished,
    // so two runs can never overlap on one server.
    @Scheduled(fixedDelayString = "${givelives.pending-cleanup-interval-ms}", initialDelay = 60_000)
    public void run() {
        try {
            cleanupService.expireStalePendingDonations();
        } catch (RuntimeException e) {
            log.error("Pending donation cleanup failed", e);
        }
    }
}