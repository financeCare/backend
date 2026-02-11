package com.example.capstone.dto;

import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
public class UserSettingDto {
    private boolean notificationsEnabled;
    private LocalTime defaultNotifyTime;
    private int notify_due_days_before;
    private double monthly_repayment_budget;
    private UUID default_strategy;
}
