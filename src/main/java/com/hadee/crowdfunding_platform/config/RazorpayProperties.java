package com.hadee.crowdfunding_platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "givelives.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret) {}