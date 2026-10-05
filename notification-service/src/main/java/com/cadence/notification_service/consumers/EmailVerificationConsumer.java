package com.cadence.notification_service.consumers;

import com.cadence.events.EmailVerificationEvent;
import com.cadence.events.EventEnvelope;
import com.cadence.events.Topics;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.SagaContext;
import com.cadence.notification_service.services.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * No database here, so no inbox: delivery is at-least-once and a redelivered event can send a duplicate email.
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationConsumer {
    private final WorkerService workerService;
    private final EventCodec codec;

    @KafkaListener(topics = Topics.EMAIL_VERIFICATION_TOPIC, groupId = "notification-service-email-verification")
    public void listenToEmailVerificationRequests(String payload) {
        EventEnvelope<EmailVerificationEvent> envelope = codec.decode(payload, EmailVerificationEvent.class);
        try (SagaContext.Scope ignored = SagaContext.join(envelope.sagaId(), envelope.eventId())) {
            workerService.sendEmailVerificationMail(envelope.payload());
        }
    }
}
