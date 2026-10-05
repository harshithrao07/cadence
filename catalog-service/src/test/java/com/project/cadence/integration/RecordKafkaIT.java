package com.project.cadence.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.RecordCreatedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.EventCodec;
import com.project.cadence.producers.RecordCreatedProducer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end through the outbox: producer writes the row in a transaction, the relay delivers it.
 */
class RecordKafkaIT extends BaseIntegrationTest {

    @Autowired RecordCreatedProducer producer;
    @Autowired EventCodec codec;
    @Autowired PlatformTransactionManager transactionManager;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void subscribe() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "record-test-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 500);
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(Topics.RECORD_CREATED_TOPIC));
        consumer.poll(Duration.ofSeconds(2));
    }

    @AfterEach
    void unsubscribe() {
        if (consumer != null) consumer.close();
    }

    @Test
    void send_isRelayedAsEnvelope_keyedByRecordId() {
        RecordCreatedEvent event = new RecordCreatedEvent(
                "record-42",
                "Certified Lover Boy",
                List.of("artist-1"),
                "https://cdn.example/cover.jpg",
                List.of("alice@example.com")
        );

        new TransactionTemplate(transactionManager).executeWithoutResult(s -> producer.send(event));

        ConsumerRecord<String, String> received = pollForOne(Duration.ofSeconds(15));
        assertThat(received).as("expected one message on %s", Topics.RECORD_CREATED_TOPIC).isNotNull();
        assertThat(received.key()).isEqualTo("record-42");

        EventEnvelope<RecordCreatedEvent> envelope = codec.decode(received.value(), RecordCreatedEvent.class);
        assertThat(envelope.type()).isEqualTo("RecordCreatedEvent");
        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.sagaId()).isNotNull();
        assertThat(envelope.payload()).isEqualTo(event);
    }

    private ConsumerRecord<String, String> pollForOne(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(500));
            if (!batch.isEmpty()) return batch.iterator().next();
        }
        return null;
    }
}
