package com.example.capstone.messaging;

import com.example.capstone.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Delivers due-date reminders. Delivery is at-least-once, so {@link NotificationService#deliverDebtReminder}
 * de-duplicates on the notification log. Exceptions are retried by the listener retry settings and then
 * dead-lettered.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notifications.async.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DebtReminderListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = ReminderMessagingConfig.QUEUE)
    public void onReminder(DebtReminderMessage message) {
        notificationService.deliverDebtReminder(
                message.userId(), message.ruleId(), message.debtId(), message.daysBefore());
    }

    @RabbitListener(queues = ReminderMessagingConfig.DEAD_LETTER_QUEUE)
    public void onReminderDead(DebtReminderMessage message) {
        log.error("Reminder for debt {} (D-{}) moved to the dead-letter queue", message.debtId(), message.daysBefore());
    }
}
