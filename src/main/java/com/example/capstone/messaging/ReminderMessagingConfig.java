package com.example.capstone.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for due-date reminders. Only active when {@code app.notifications.async.enabled=true}.
 *
 * <pre>
 * reminder.exchange --reminder.debt--> reminder.debt --(rejected after the retries)--> reminder.dlx --> reminder.debt.dlq
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "app.notifications.async.enabled", havingValue = "true")
public class ReminderMessagingConfig {

    public static final String EXCHANGE = "reminder.exchange";
    public static final String ROUTING_KEY = "reminder.debt";
    public static final String QUEUE = "reminder.debt";
    public static final String DEAD_LETTER_EXCHANGE = "reminder.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "reminder.debt.dead";
    public static final String DEAD_LETTER_QUEUE = "reminder.debt.dlq";

    @Bean
    DirectExchange reminderExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange reminderDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue reminderQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue reminderDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding reminderBinding(Queue reminderQueue, DirectExchange reminderExchange) {
        return BindingBuilder.bind(reminderQueue).to(reminderExchange).with(ROUTING_KEY);
    }

    @Bean
    Binding reminderDeadLetterBinding(Queue reminderDeadLetterQueue, DirectExchange reminderDeadLetterExchange) {
        return BindingBuilder.bind(reminderDeadLetterQueue).to(reminderDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
