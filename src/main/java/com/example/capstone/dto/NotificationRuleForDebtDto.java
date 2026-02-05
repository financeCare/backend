package com.example.capstone.dto;

import com.example.capstone.enums.RefType;

public class NotificationRuleForDebtDto {
    private RefType refType;
    private String refId;
    private String title;
    private String bodyTemplate;
    private int remindDaysBefore;
}
