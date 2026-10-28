package com.hadee.crowdfunding_platform.exception;

public class WebhookProcessingException extends RuntimeException {

    public WebhookProcessingException(String message) {
        super(message);
    }
}