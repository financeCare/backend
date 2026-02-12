package com.example.capstone.entity;

import com.example.capstone.enums.RefType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_rule",
        indexes = {
                @Index(name = "idx_rule_user", columnList = "user_id"),
                @Index(name = "idx_rule_ref", columnList = "ref_type, ref_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 30)
    private RefType refType;

    @Column(name = "ref_id", length = 64)
    private String refId; // เช่น debtId (int) ก็เก็บเป็น string ได้

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "body_template", nullable = false, length = 500)
    private String bodyTemplate;

    @Builder.Default
    @Column(name = "remind_days_before", nullable = false)
    private int remindDaysBefore = 3;

    @Builder.Default
    @Column(name = "time_of_day")
    private LocalTime timeOfDay = LocalTime.of(9, 0); // 09:00

    @Builder.Default
    @Column(name = "timezone", length = 64)
    private String timezone = "Asia/Bangkok";

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

