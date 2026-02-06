package com.example.capstone.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UserDeviceRequest {
    private String deviceKey;
    private String fcmToken;
    private String platform;
    private String deviceName;
}
