package com.example.capstone.repository;

import com.example.capstone.entity.NotificationRule;
import com.example.capstone.enums.RefType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {

    List<NotificationRule> findAllByUserId(UUID userId);

    List<NotificationRule> findAllByUserIdAndIsActiveTrue(UUID userId);

    Optional<NotificationRule> findByUserIdAndRefTypeAndRefId(UUID userId, RefType refType, String refId);

    List<NotificationRule> findAllByRefTypeAndIsActiveTrue(RefType refType);
    Optional<NotificationRule> findByRuleIdAndUserId(UUID notificationRuleId, UUID userId);
    Optional<NotificationRule> findByUserIdAndRefTypeAndRefIdAndIsActiveTrue(UUID userId, RefType refType, String refId);
    List<NotificationRule> findAllByIsActive(boolean isActive);
}
