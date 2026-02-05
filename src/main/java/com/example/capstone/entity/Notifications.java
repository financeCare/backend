//package com.example.capstone.entity;
//
//import jakarta.persistence.*;
//import lombok.AllArgsConstructor;
//import lombok.Getter;
//import lombok.NoArgsConstructor;
//import lombok.Setter;
//import org.hibernate.annotations.CreationTimestamp;
//
//import java.security.Timestamp;
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@Entity
//@Table(name = "notifications")
//@Getter
//@Setter
//@AllArgsConstructor
//@NoArgsConstructor
//
//public class Notifications {
//    @Id
//    @GeneratedValue(strategy = GenerationType.UUID)
//    @Column(name = "notification_id")
//    private UUID notificationId;
//    @Column(name = "user_id", nullable = false)
//    private UUID userId;
//    @Column(name = "type")
//    private String type;
//    @Column(name = "message", nullable = false, length = 1000)
//    private String message;
//    @Column(name = "is_read", nullable = false)
//    private Boolean isRead;
//    @CreationTimestamp
//    @Column(name = "created_at", nullable = false)
//    private LocalDateTime createdAt;
//}
