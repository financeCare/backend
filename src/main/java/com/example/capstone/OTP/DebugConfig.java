package com.example.capstone.OTP;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DebugConfig {

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
