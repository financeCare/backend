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
 * RabbitMQ topology for the asynchronous slip OCR pipeline. Only active when
 * {@code app.slip.async.enabled=true}, so deployments without a broker are unaffected.
 *
 * <pre>
 * slip.exchange --slip.ocr--> slip.ocr  --(rejected after the retries)--> slip.dlx --> slip.ocr.dlq
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "app.slip.async.enabled", havingValue = "true")
public class SlipMessagingConfig {

    public static final String EXCHANGE = "slip.exchange";
    public static final String ROUTING_KEY = "slip.ocr";
    public static final String QUEUE = "slip.ocr";
    public static final String DEAD_LETTER_EXCHANGE = "slip.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "slip.ocr.dead";
    public static final String DEAD_LETTER_QUEUE = "slip.ocr.dlq";

    @Bean
    DirectExchange slipExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange slipDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue slipOcrQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue slipOcrDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding slipOcrBinding(Queue slipOcrQueue, DirectExchange slipExchange) {
        return BindingBuilder.bind(slipOcrQueue).to(slipExchange).with(ROUTING_KEY);
    }

    @Bean
    Binding slipOcrDeadLetterBinding(Queue slipOcrDeadLetterQueue, DirectExchange slipDeadLetterExchange) {
        return BindingBuilder.bind(slipOcrDeadLetterQueue).to(slipDeadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }
}
