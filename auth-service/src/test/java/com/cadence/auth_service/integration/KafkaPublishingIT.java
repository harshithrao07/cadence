package com.cadence.auth_service.integration;

import com.cadence.auth_service.dto.auth.RegisterRequestDTO;
import com.cadence.auth_service.producers.EmailVerificationProducer;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.service.AuthenticationService;
import com.cadence.events.EmailVerificationEvent;
import com.cadence.events.EventEnvelope;
import com.cadence.events.Topics;
import com.cadence.events.UserRegisteredEvent;
import com.cadence.messaging.EventCodec;
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
 * End-to-end through the outbox: the business transaction writes the event, the relay delivers it as an envelope.
 */
class KafkaPublishingIT extends BaseIntegrationTest {

    @Autowired AuthenticationService authenticationService;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationProducer emailVerificationProducer;
    @Autowired EventCodec codec;
    @Autowired PlatformTransactionManager transactionManager;

    private KafkaConsumer<String, String> userConsumer;
    private KafkaConsumer<String, String> emailConsumer;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userConsumer = newConsumer(Topics.USER_REGISTERED_TOPIC);
        emailConsumer = newConsumer(Topics.EMAIL_VERIFICATION_TOPIC);
    }

    @AfterEach
    void tearDown() {
        if (userConsumer != null) userConsumer.close();
        if (emailConsumer != null) emailConsumer.close();
    }

    @Test
    void register_relaysUserRegisteredEvent_withSavedUserId() {
        authenticationService.register(new RegisterRequestDTO("Alice", "alice@example.com", "StrongPass1!"));

        String savedUserId = userRepository.findByEmail("alice@example.com").orElseThrow().getId();

        ConsumerRecord<String, String> received = pollForKey(userConsumer, savedUserId, Duration.ofSeconds(15));
        assertThat(received).as("expected one message on %s", Topics.USER_REGISTERED_TOPIC).isNotNull();
        assertThat(received.key()).isEqualTo(savedUserId);

        EventEnvelope<UserRegisteredEvent> envelope = codec.decode(received.value(), UserRegisteredEvent.class);
        assertThat(envelope.type()).isEqualTo("UserRegisteredEvent");
        assertThat(envelope.sagaId()).isNotNull();
        assertThat(envelope.payload().userId()).isEqualTo(savedUserId);
    }

    @Test
    void emailVerificationProducer_relaysEnvelope_keyedByEmail() {
        EmailVerificationEvent event = new EmailVerificationEvent("bob@example.com", "https://example.com/verify?token=abc");

        new TransactionTemplate(transactionManager).executeWithoutResult(s -> emailVerificationProducer.send(event));

        ConsumerRecord<String, String> received = pollForKey(emailConsumer, "bob@example.com", Duration.ofSeconds(15));
        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo("bob@example.com");
        assertThat(codec.decode(received.value(), EmailVerificationEvent.class).payload()).isEqualTo(event);
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, topic + "-test-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 500);
        KafkaConsumer<String, String> c = new KafkaConsumer<>(props);
        c.subscribe(List.of(topic));
        c.poll(Duration.ofSeconds(2));
        return c;
    }

    /** Other ITs share the broker and publish to the same topics, so match on the key. */
    private static ConsumerRecord<String, String> pollForKey(KafkaConsumer<String, String> consumer, String key, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : batch) {
                if (key.equals(record.key())) return record;
            }
        }
        return null;
    }
}
