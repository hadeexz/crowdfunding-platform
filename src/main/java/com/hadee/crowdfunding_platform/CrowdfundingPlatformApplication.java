package com.hadee.crowdfunding_platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan; // 1. Add this import

@SpringBootApplication
@ConfigurationPropertiesScan // 2. Add this annotation here
public class CrowdfundingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrowdfundingPlatformApplication.class, args);
    }

}