package com.cadence.notification_service.integration;

import com.cadence.notification_service.dto.Topics;
import jakarta.mail.internet.MimeMessage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationKafkaIT extends BaseIntegrationTest {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void emailVerificationEvent_triggersOneOutboundEmail() throws Exception {
        MimeMessage stub = Mockito.mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(stub);

        String payload = "{\"email\":\"alice@example.com\",\"verificationLink\":\"https://cadence.test/verify?t=abc\"}";
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

        String payload = "{"
                + "\"recordId\":\"rec-1\","
                + "\"recordTitle\":\"Certified Lover Boy\","
                + "\"artists\":[\"Drake\"],"
                + "\"coverUrl\":\"https://cdn/cover.jpg\","
                + "\"followerEmails\":[\"alice@example.com\",\"bob@example.com\"]"
                + "}";
        kafkaTemplate.send(Topics.RECORD_CREATED_TOPIC, "rec-1", payload);
        kafkaTemplate.flush();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> verify(mailSender, times(2)).send(any(MimeMessage.class)));
    }
}
