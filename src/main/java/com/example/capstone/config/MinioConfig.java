package com.example.capstone.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

        //TODO change minio to public url for production
    private final String url = "http://10.4.88.37:9000";
    private final String accessKey = "admin";
    private final String secretKey = "password123";

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}
