package com.example.capstone.repository;

import com.example.capstone.entity.ReceiverMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiverMappingRepository extends JpaRepository<ReceiverMapping, Long> {
    List<ReceiverMapping> findByUserId(UUID userId);
    Optional<ReceiverMapping> findByUserIdAndReceiverName(UUID userId, String receiverName);
}
