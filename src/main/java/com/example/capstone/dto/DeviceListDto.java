package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor

public class DeviceListDto {
    private UUID deviceId;
    private String deviceName;
    private String platform;
    private LocalDateTime lastSeen;
}
