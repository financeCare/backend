package com.example.capstone.repository;

import com.example.capstone.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByFcmToken(String fcmToken);
    List<UserDevice> findAllByUserIdAndIsActiveTrue(UUID userId);
    long deleteByFcmToken(String fcmToken);
    boolean existsByFcmToken(String fcmToken);
    Optional<UserDevice> findByDeviceKeyAndUserId(String deviceKey, UUID userId);

}

