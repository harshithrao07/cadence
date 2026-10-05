package com.cadence.notification_service.integration;

import com.cadence.events.EmailVerificationEvent;
import com.cadence.events.EventEnvelope;
import com.cadence.events.RecordCreatedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.EventCodec;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationKafkaIT extends BaseIntegrationTest {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired EventCodec codec;

    @Test
    void emailVerificationEvent_triggersOneOutboundEmail() throws Exception {
        MimeMessage stub = Mockito.mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(stub);

        String payload = codec.encode(EventEnvelope.of(UUID.randomUUID(), "EmailVerificationEvent",
                new EmailVerificationEvent("alice@example.com", "https://cadence.test/verify?t=abc")));
        kafkaTemplate.send(Topics.EMAIL_VERIFICATION_TOPIC, "alice@example.com", payload);
        kafkaTemplate.flush();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> verify(mailSender, atLeastOnce()).send(any(MimeMessage.class)));

        verify(stub).setSubject("Verify your email", "UTF-8");
    }

    @Test
    void recordCreatedEvent_sendsOneEmailPerFollower() {
        MimeMessage stub = Mockito.mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(stub);

        String payload = codec.encode(EventEnvelope.of(UUID.randomUUID(), "RecordCreatedEvent",
                new RecordCreatedEvent("rec-1", "Certified Lover Boy", List.of("Drake"), "https://cdn/cover.jpg",
                        List.of("alice@example.com", "bob@example.com"))));
        kafkaTemplate.send(Topics.RECORD_CREATED_TOPIC, "rec-1", payload);
        kafkaTemplate.flush();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> verify(mailSender, times(2)).send(any(MimeMessage.class)));
    }

    @Test
    void malformedMessage_skipsRetries_andLandsOnDlt() {
        String dltTopic = Topics.EMAIL_VERIFICATION_TOPIC + Topics.DLT_SUFFIX;
        String legacyPayload = "{\"email\":\"dlt@example.com\",\"verificationLink\":\"https://cadence.test/verify\"}";

        try (KafkaConsumer<String, String> dlt = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.METADATA_MAX_AGE_CONFIG, 500,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            dlt.subscribe(List.of(dltTopic));

            kafkaTemplate.send(Topics.EMAIL_VERIFICATION_TOPIC, "dlt@example.com", legacyPayload);
            kafkaTemplate.flush();

            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(20))
                    .until(() -> {
                        dlt.poll(Duration.ofMillis(500)).forEach(received::add);
                        return received.stream().anyMatch(r -> "dlt@example.com".equals(r.key()));
                    });

            assertThat(received).filteredOn(r -> "dlt@example.com".equals(r.key()))
                    .singleElement()
                    .satisfies(r -> assertThat(r.value()).isEqualTo(legacyPayload));
        }
    }
}
