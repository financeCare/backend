package com.example.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
public class UserSettingDto {
    private boolean notificationsEnabled;
    private LocalTime defaultNotifyTime;

    @JsonProperty("notify_due_days_before")
    private int notifyDueDaysBefore;

    @PositiveOrZero(message = "Monthly repayment budget must be zero or positive")
    @JsonProperty("monthly_repayment_budget")
    private double monthlyRepaymentBudget;

    @JsonProperty("default_strategy")
    private UUID defaultStrategy;

    private String skills;
}
