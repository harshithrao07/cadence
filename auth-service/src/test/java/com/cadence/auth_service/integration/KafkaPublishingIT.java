package com.cadence.auth_service.integration;

import com.cadence.auth_service.dto.Topics;
import com.cadence.auth_service.dto.auth.RegisterRequestDTO;
import com.cadence.auth_service.events.EmailVerificationEvent;
import com.cadence.auth_service.events.UserCreatedEvent;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.service.AuthenticationService;
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

class KafkaPublishingIT extends BaseIntegrationTest {

    @Autowired AuthenticationService authenticationService;
    @Autowired UserRepository userRepository;
    @Autowired KafkaTemplate<String, EmailVerificationEvent> emailKafkaTemplate;

    private KafkaConsumer<String, UserCreatedEvent> userConsumer;
    private KafkaConsumer<String, EmailVerificationEvent> emailConsumer;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userConsumer = newConsumer(UserCreatedEvent.class, Topics.USER_CREATED_TOPIC);
        emailConsumer = newConsumer(EmailVerificationEvent.class, Topics.EMAIL_VERIFICATION_TOPIC);
    }

    @AfterEach
    void tearDown() {
        if (userConsumer != null) userConsumer.close();
        if (emailConsumer != null) emailConsumer.close();
    }

    @Test
    void register_publishesUserCreatedEvent_withSavedUserId() {
        authenticationService.register(new RegisterRequestDTO("Alice", "alice@example.com", "StrongPass1!"));

        String savedUserId = userRepository.findByEmail("alice@example.com").orElseThrow().getId();

        ConsumerRecord<String, UserCreatedEvent> received = pollForOne(userConsumer, Duration.ofSeconds(15));
        assertThat(received).as("expected one message on %s", Topics.USER_CREATED_TOPIC).isNotNull();
        assertThat(received.value().getUserId()).isEqualTo(savedUserId);
    }

    @Test
    void emailVerificationProducer_serializesEvent_andRoutesByEmailKey() {
        EmailVerificationEvent event = new EmailVerificationEvent("bob@example.com", "https://example.com/verify?token=abc");

        emailKafkaTemplate.send(Topics.EMAIL_VERIFICATION_TOPIC, event.getEmail(), event);
        emailKafkaTemplate.flush();

        ConsumerRecord<String, EmailVerificationEvent> received = pollForOne(emailConsumer, Duration.ofSeconds(15));
        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo("bob@example.com");
        assertThat(received.value().getEmail()).isEqualTo("bob@example.com");
        assertThat(received.value().getVerificationLink()).isEqualTo("https://example.com/verify?token=abc");
    }

    private <T> KafkaConsumer<String, T> newConsumer(Class<T> valueType, String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, valueType.getSimpleName() + "-test-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, valueType.getName());
        KafkaConsumer<String, T> c = new KafkaConsumer<>(props);
        c.subscribe(List.of(topic));
        c.poll(Duration.ofSeconds(2));
        return c;
    }

    private static <T> ConsumerRecord<String, T> pollForOne(KafkaConsumer<String, T> consumer, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, T> batch = consumer.poll(Duration.ofMillis(500));
            if (!batch.isEmpty()) return batch.iterator().next();
        }
        return null;
    }
}
