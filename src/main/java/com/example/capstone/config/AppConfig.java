package com.example.capstone.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(LineConfig.class)
@EnableScheduling
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters()
                .add(0, new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8));
        return restTemplate;
    }
}
