package com.example.capstone.config;
 
import java.io.File;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Profile;
import java.io.FileInputStream;
import java.io.InputStream;

@Configuration
@Profile("!test")
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        String keyPath = System.getenv("FIREBASE_SERVICE_ACCOUNT");
        if (keyPath == null || keyPath.isBlank()) {
            System.err.println("WARNING: FIREBASE_SERVICE_ACCOUNT env var is missing. Push notifications will be disabled.");
            return null;
        }

        File file = new File(keyPath);
        if (!file.exists()) {
            System.err.println("ERROR: Firebase service account file not found at: " + keyPath);
            System.err.println("Push notifications will be disabled. Please check your FIREBASE_SERVICE_ACCOUNT environment variable.");
            return null;
        }

        try (InputStream is = new FileInputStream(file)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            return FirebaseApp.initializeApp(options);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to initialize Firebase: " + e.getMessage());
            return null;
        }
    }
}
