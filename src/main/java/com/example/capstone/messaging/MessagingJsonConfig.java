package com.example.capstone.messaging;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JSON instead of Java serialization for every RabbitMQ pipeline, so only the message records can be deserialized. */
@Configuration
@ConditionalOnExpression("'${app.slip.async.enabled:false}' == 'true' or '${app.notifications.async.enabled:false}' == 'true'")
public class MessagingJsonConfig {

    @Bean
    MessageConverter amqpMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
