package com.example.capstone.messaging;

import com.example.capstone.service.SlipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumers of the slip OCR pipeline. Delivery is at-least-once, so {@link SlipService#processQueuedSlip}
 * is idempotent. An exception thrown here is retried by the listener retry settings
 * ({@code spring.rabbitmq.listener.simple.retry.*}) and, once those are exhausted, the message is rejected
 * and routed to the dead-letter queue.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.slip.async.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SlipOcrListener {

    private final SlipService slipService;

    @RabbitListener(queues = SlipMessagingConfig.QUEUE)
    public void onSlipQueued(SlipOcrMessage message) {
        log.info("Processing queued slip {}", message.slipId());
        slipService.processQueuedSlip(message.slipId());
    }

    @RabbitListener(queues = SlipMessagingConfig.DEAD_LETTER_QUEUE)
    public void onSlipDead(SlipOcrMessage message) {
        log.error("Slip {} moved to the dead-letter queue after repeated failures", message.slipId());
        slipService.markFailed(message.slipId());
    }
}
