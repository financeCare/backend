package com.example.capstone.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes payment events to Kafka, keyed by debt id so all events of one debt stay in order on one partition.
 * Only active when {@code app.events.kafka.enabled=true}. Failures are logged, never thrown: the payment is
 * already committed and the event log is a secondary record.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.events.kafka.enabled", havingValue = "true")
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    public static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaPaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void publish(PaymentEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.debtId().toString(), objectMapper.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Could not append payment event {} to Kafka", event.eventId(), error);
                        }
                    });
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Could not publish payment event {}", event.eventId(), e);
        }
    }
}
