package com.example.capstone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserDeviceRequest {
    @NotBlank(message = "Device key is required")
    private String deviceKey;

    @NotBlank(message = "FCM token is required")
    private String fcmToken;

    @NotBlank(message = "Platform is required")
    private String platform;

    @NotBlank(message = "Device name is required")
    private String deviceName;
}
