package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_device", indexes = {
                @Index(name = "idx_user_device_user", columnList = "user_id")
}, uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_device_token", columnNames = { "fcm_token" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "device_id", nullable = false)
        private UUID deviceId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "fcm_token", nullable = false, columnDefinition = "text")
        private String fcmToken;

        @Column(name = "platform", length = 20)
        private String platform; // android / ios / web

        @Column(name = "device_name", length = 120)
        private String deviceName;

        @Builder.Default
        @Column(name = "is_active", nullable = false)
        private Boolean active = true;

        @Column(name = "last_seen")
        private LocalDateTime lastSeen;

        @CreationTimestamp
        @Column(name = "created_at", updatable = false)
        private LocalDateTime createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at")
        private LocalDateTime updatedAt;

        @Column(name = "device_key", nullable = false, length = 64, unique = true)
        private String deviceKey;

}
