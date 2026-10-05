package com.cadence.auth_service.producers;

import com.cadence.events.EmailVerificationEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Records {@link EmailVerificationEvent} in the outbox, keyed by email; the relay delivers it after the
 * verification token's transaction commits.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationProducer {
    private final OutboxPublisher outboxPublisher;

    public void send(EmailVerificationEvent event) {
        outboxPublisher.publish(Topics.EMAIL_VERIFICATION_TOPIC, "user", event.email(), event);
    }
}
