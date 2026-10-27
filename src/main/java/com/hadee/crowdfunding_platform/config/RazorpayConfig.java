package com.hadee.crowdfunding_platform.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;



@ConditionalOnProperty(name = "givelives.payment.provider", havingValue = "razorpay")
@Configuration
public class RazorpayConfig {

    @Bean
    public RazorpayClient razorpayClient(RazorpayProperties props) throws RazorpayException {
        return new RazorpayClient(props.keyId(), props.keySecret());
    }
}