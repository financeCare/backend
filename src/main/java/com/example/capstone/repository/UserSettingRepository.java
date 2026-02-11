package com.example.capstone.repository;

import com.example.capstone.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserSettingRepository extends JpaRepository<UserSetting, UUID> {
    UserSetting findByUserId(UUID userId);
}
