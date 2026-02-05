//package com.example.capstone.repository;
//
//import com.example.capstone.entity.Category;
//import com.example.capstone.entity.Notifications;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//public interface NotificationRepository extends JpaRepository<Notifications, UUID> {
//    Page<Notifications> findByUserId(UUID userId, Pageable pageable);
//    Page<Notifications> findByUserIdAndIsRead(UUID userId, Boolean isRead, Pageable pageable);
//    long countByUserIdAndIsRead(UUID userId, Boolean isRead);
//    boolean existsByUserIdAndTypeAndCreatedAtBetween(
//            UUID userId,
//            String type,
//            LocalDateTime start,
//            LocalDateTime end
//    );
//}