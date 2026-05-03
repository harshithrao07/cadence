package com.cadence.notification_service.consumers;

import com.cadence.notification_service.dto.Topics;
import com.cadence.notification_service.events.EmailVerificationEvent;
import com.cadence.notification_service.services.WorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationConsumer {
    private final WorkerService workerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = Topics.EMAIL_VERIFICATION_TOPIC, groupId = "notification-service-email-verification")
    public void listenToEmailVerificationRequests(String payload) throws Exception {
        EmailVerificationEvent event = objectMapper.readValue(payload, EmailVerificationEvent.class);
        workerService.sendEmailVerificationMail(event);
    }
}
