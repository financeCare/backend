package com.example.capstone.entity;

import com.example.capstone.enums.NotificationChannel;
import com.example.capstone.enums.NotificationStatus;
import com.example.capstone.enums.RefType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_log",
        indexes = {
                @Index(name = "idx_log_user_time", columnList = "user_id, sent_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id", nullable = false)
    private UUID logId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 30)
    private RefType refType;

    @Column(name = "ref_id", length = 64)
    private String refId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
