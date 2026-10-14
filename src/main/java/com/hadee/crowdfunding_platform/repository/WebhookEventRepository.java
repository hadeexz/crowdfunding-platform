package com.hadee.crowdfunding_platform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hadee.crowdfunding_platform.entity.WebhookEvent;
import com.hadee.crowdfunding_platform.entity.enums.WebhookProcessingStatus;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByRazorpayEventId(String razorpayEventId);

    boolean existsByRazorpayEventId(String razorpayEventId);

    List<WebhookEvent> findByProcessingStatus(WebhookProcessingStatus status);
}