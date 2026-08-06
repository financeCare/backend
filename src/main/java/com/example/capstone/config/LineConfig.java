package com.example.capstone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "line")
@Data
public class LineConfig {
    private String channelId;
}
