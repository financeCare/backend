package com.example.capstone.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Rebuilds a read model (total paid per debt) by replaying the whole payment topic from the first offset.
 * Duplicate deliveries are ignored by event id, so replaying twice gives the same result. This is what a
 * queue cannot do: the log is still there after it was consumed.
 */
public class PaymentEventReplayer {

    private final String bootstrapServers;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public PaymentEventReplayer(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /** Total paid per debt, built from every event that is in the topic when the replay starts. */
    public Map<UUID, BigDecimal> replayTotals() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        Map<UUID, BigDecimal> totals = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> partitions = consumer.partitionsFor(KafkaPaymentEventPublisher.TOPIC).stream()
                    .map(p -> new TopicPartition(p.topic(), p.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);

            // read until every partition has reached the end offset it had when the replay started
            while (partitions.stream().anyMatch(tp -> consumer.position(tp) < end.get(tp))) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    apply(record.value(), seen, totals);
                }
            }
        }
        return totals;
    }

    private void apply(String json, Set<UUID> seen, Map<UUID, BigDecimal> totals) {
        try {
            PaymentEvent event = objectMapper.readValue(json, PaymentEvent.class);
            if (seen.add(event.eventId())) {
                totals.merge(event.debtId(), event.amount(), BigDecimal::add);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable payment event: " + json, e);
        }
    }
}
