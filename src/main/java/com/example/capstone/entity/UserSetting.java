package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "user_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSetting {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Master switch
    @Column(name = "notifications_enabled", nullable = false)
    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Builder.Default
    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    // Defaults for notification rules
    @Builder.Default
    @Column(name = "default_remind_days_before", nullable = false)
    private Integer defaultRemindDaysBefore = 3;

    @Builder.Default
    @Column(name = "default_notify_time", nullable = false)
    private LocalTime defaultNotifyTime = LocalTime.of(9, 0);

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Bangkok";

    @Column(name = "current_profession")
    private String currentProfession;

    @Column(name = "skills")
    private String skills; // Stored as comma-separated values

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
