package com.example.capstone.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notifications.async.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RabbitDebtReminderPublisher implements DebtReminderPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(DebtReminderMessage message) {
        rabbitTemplate.convertAndSend(
                ReminderMessagingConfig.EXCHANGE, ReminderMessagingConfig.ROUTING_KEY, message);
    }
}
