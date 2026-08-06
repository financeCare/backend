package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "slips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sender_bank")
    private String senderBank;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transfer_date")
    private LocalDateTime transferDate;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "image_path", length = 500, nullable = false)
    private String imagePath;

    @Column(name = "qr_data", columnDefinition = "TEXT")
    private String qrData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_texts", columnDefinition = "jsonb")
    private String rawTexts;

    @Column(name = "status", length = 20)
    private String status = "processed";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "processed";
        }
    }
}
