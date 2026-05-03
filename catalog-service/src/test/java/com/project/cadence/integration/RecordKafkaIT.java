package com.project.cadence.integration;

import com.project.cadence.dto.Topics;
import com.project.cadence.events.RecordCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecordKafkaIT extends BaseIntegrationTest {

    @Autowired KafkaTemplate<String, RecordCreatedEvent> kafkaTemplate;

    private KafkaConsumer<String, RecordCreatedEvent> consumer;

    @BeforeEach
    void subscribe() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "record-test-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RecordCreatedEvent.class.getName());
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(Topics.RECORD_CREATED_TOPIC));
        consumer.poll(Duration.ofSeconds(2));
    }

    @AfterEach
    void unsubscribe() {
        if (consumer != null) consumer.close();
    }

    @Test
    void publish_serializesEvent_andRoutesToTopic() {
        RecordCreatedEvent event = new RecordCreatedEvent(
                "record-42",
                "Certified Lover Boy",
                List.of("artist-1"),
                "https://cdn.example/cover.jpg",
                List.of("alice@example.com")
        );

        kafkaTemplate.send(Topics.RECORD_CREATED_TOPIC, event.getRecordId(), event);
        kafkaTemplate.flush();

        ConsumerRecord<String, RecordCreatedEvent> received = pollForOne(Duration.ofSeconds(15));
        assertThat(received).as("expected one message on %s", Topics.RECORD_CREATED_TOPIC).isNotNull();
        assertThat(received.topic()).isEqualTo(Topics.RECORD_CREATED_TOPIC);
        assertThat(received.key()).isEqualTo("record-42");
        assertThat(received.value().getRecordId()).isEqualTo("record-42");
        assertThat(received.value().getRecordTitle()).isEqualTo("Certified Lover Boy");
        assertThat(received.value().getArtists()).containsExactly("artist-1");
        assertThat(received.value().getFollowerEmails()).containsExactly("alice@example.com");
    }

    private ConsumerRecord<String, RecordCreatedEvent> pollForOne(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, RecordCreatedEvent> batch = consumer.poll(Duration.ofMillis(500));
            if (!batch.isEmpty()) return batch.iterator().next();
        }
        return null;
    }
}
