package com.example.capstone.repository;

import com.example.capstone.entity.NotificationLog;
import com.example.capstone.enums.NotificationStatus;
import com.example.capstone.enums.RefType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findAllByUserId(UUID userId, Pageable pageable);
    Page<NotificationLog> findAllByUserIdAndStatusIsNot(UUID userId,NotificationStatus notificationStatus, Pageable pageable);

    Page<NotificationLog> findAllByUserIdAndRefTypeAndStatusIsNot(UUID userId, RefType refType,NotificationStatus notificationStatus, Pageable pageable);

    boolean existsByRuleIdAndStatusAndSentAtBetween(
            UUID ruleId,
            NotificationStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    boolean existsByRuleIdAndStatusAndSentAtAfter(
            UUID ruleId,
            NotificationStatus status,
            LocalDateTime after
    );

    boolean existsByRuleIdAndScheduleKeyAndStatusAndSentAtBetween(
            UUID ruleId,
            String scheduleKey,
            NotificationStatus status,
            LocalDateTime start,
            LocalDateTime end
    );


}
