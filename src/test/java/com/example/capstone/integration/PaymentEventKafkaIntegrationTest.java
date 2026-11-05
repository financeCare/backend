package com.example.capstone.integration;

import com.example.capstone.messaging.KafkaPaymentEventPublisher;
import com.example.capstone.messaging.PaymentEvent;
import com.example.capstone.messaging.PaymentEventReplayer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The payment event log against a real single-node Kafka: append, replay from the start, duplicate handling. */
@Testcontainers(disabledWithoutDocker = true)
class PaymentEventKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    static KafkaPaymentEventPublisher publisher;

    @BeforeAll
    static void start() throws Exception {
        Properties admin = new Properties();
        admin.put("bootstrap.servers", kafka.getBootstrapServers());
        try (AdminClient client = AdminClient.create(admin)) {
            client.createTopics(List.of(new NewTopic(KafkaPaymentEventPublisher.TOPIC, 3, (short) 1)))
                    .all().get(30, TimeUnit.SECONDS);
        }
        DefaultKafkaProducerFactory<String, String> producers = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all"));
        publisher = new KafkaPaymentEventPublisher(new KafkaTemplate<>(producers), new ObjectMapper());
    }

    private static PaymentEvent event(UUID debtId, String amount) {
        return new PaymentEvent(UUID.randomUUID(), debtId, UUID.randomUUID(), new BigDecimal(amount),
                LocalDate.of(2026, 11, 5), null, Instant.now());
    }

    @Test
    void replayRebuildsTheTotalsPerDebt_andIsRepeatable_andIgnoresDuplicates() throws Exception {
        UUID debtA = UUID.randomUUID();
        UUID debtB = UUID.randomUUID();
        PaymentEvent duplicated = event(debtA, "100.00");

        publisher.publish(duplicated);
        publisher.publish(duplicated);                // at-least-once producer retry: same eventId twice
        publisher.publish(event(debtA, "50.25"));
        publisher.publish(event(debtB, "300.00"));
        Thread.sleep(2000);                           // send() is asynchronous; let the batch go out

        PaymentEventReplayer replayer = new PaymentEventReplayer(kafka.getBootstrapServers());
        Map<UUID, BigDecimal> first = replayer.replayTotals();
        Map<UUID, BigDecimal> second = replayer.replayTotals();

        assertEquals(new BigDecimal("150.25"), first.get(debtA));
        assertEquals(new BigDecimal("300.00"), first.get(debtB));
        assertEquals(first, second);
    }
}
