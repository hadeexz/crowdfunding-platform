package com.hadee.crowdfunding_platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "givelives.jwt")
public record JwtProperties(String secret, long expirationMinutes) {}