package com.example.capstone.messaging;

import java.util.UUID;

/** Work item for the due-date reminder pipeline: one reminder (debt + days-before) for one user. */
public record DebtReminderMessage(UUID userId, UUID ruleId, UUID debtId, int daysBefore) {
}
