package com.example.capstone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "line")
@Data
public class LineConfig {
    private String channelId;
}
