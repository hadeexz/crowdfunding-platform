package com.hadee.crowdfunding_platform.exception;

public class AccountBannedException extends RuntimeException {

    public AccountBannedException(String message) {
        super(message);
    }
}