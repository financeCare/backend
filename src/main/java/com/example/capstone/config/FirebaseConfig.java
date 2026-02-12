package com.example.capstone.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        String keyPath = System.getenv("FIREBASE_SERVICE_ACCOUNT");
        if (keyPath == null || keyPath.isBlank()) {
            throw new IllegalStateException(
                    "Missing FIREBASE_SERVICE_ACCOUNT env var. " +
                            "Set it to the absolute path of your Firebase service account JSON file."
            );
        }

        try (InputStream is = new FileInputStream(keyPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }
}
