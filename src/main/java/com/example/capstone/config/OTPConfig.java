package com.example.capstone.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class OTPConfig {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private String port;

    @PostConstruct
    public void debug() {
        System.out.println("MAIL_HOST = " + host);
        System.out.println("MAIL_PORT = " + port);
    }
}
