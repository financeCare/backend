package com.example.capstone.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.slip.async.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RabbitSlipOcrPublisher implements SlipOcrPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(SlipOcrMessage message) {
        rabbitTemplate.convertAndSend(SlipMessagingConfig.EXCHANGE, SlipMessagingConfig.ROUTING_KEY, message);
    }
}
