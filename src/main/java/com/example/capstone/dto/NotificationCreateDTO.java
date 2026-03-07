package com.example.capstone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotificationCreateDTO {
    @NotBlank(message = "Type is required")
    private String type;

    @NotBlank(message = "Message is required")
    private String message;
}
