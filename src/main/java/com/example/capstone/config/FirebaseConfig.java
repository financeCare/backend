package com.example.capstone.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@Slf4j
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        String keyPath = System.getenv("FIREBASE_SERVICE_ACCOUNT");
        if (keyPath == null || keyPath.isBlank()) {
            log.warn("FIREBASE_SERVICE_ACCOUNT env var is missing. Push notifications will be disabled.");
            return null;
        }

        File file = new File(keyPath);
        if (!file.exists()) {
            log.error("Firebase service account file not found at: {}", keyPath);
            log.warn("Push notifications will be disabled. Please check your FIREBASE_SERVICE_ACCOUNT environment variable.");
            return null;
        }

        try (InputStream is = new FileInputStream(file)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            return FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
            return null;
        }
    }
}
