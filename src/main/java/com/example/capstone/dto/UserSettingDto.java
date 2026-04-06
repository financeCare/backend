package com.example.capstone.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
public class UserSettingDto {
    private boolean notificationsEnabled;
    private LocalTime defaultNotifyTime;
    private int notify_due_days_before;

    @PositiveOrZero(message = "Monthly repayment budget must be zero or positive")
    private double monthly_repayment_budget;

    private UUID default_strategy;

    private String currentProfession;
    private String skills;
}
