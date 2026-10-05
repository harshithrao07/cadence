package com.cadence.notification_service.consumers;

import com.cadence.events.EmailVerificationEvent;
import com.cadence.events.EventEnvelope;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.EventDecodingException;
import com.cadence.notification_service.services.WorkerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationConsumerTest {

    @Mock WorkerService workerService;
    @Spy EventCodec codec = new EventCodec();

    @InjectMocks EmailVerificationConsumer consumer;

    @Test
    void decodesEnvelope_andDelegatesToWorker() {
        EmailVerificationEvent event = new EmailVerificationEvent("alice@example.com", "https://cadence.test/verify?t=abc");
        String payload = codec.encode(EventEnvelope.of(UUID.randomUUID(), "EmailVerificationEvent", event));

        consumer.listenToEmailVerificationRequests(payload);

        ArgumentCaptor<EmailVerificationEvent> captor = ArgumentCaptor.forClass(EmailVerificationEvent.class);
        verify(workerService).sendEmailVerificationMail(captor.capture());
        assertThat(captor.getValue()).isEqualTo(event);
    }

    @Test
    void invalidPayload_throws_andDoesNotInvokeWorker() {
        assertThatThrownBy(() -> consumer.listenToEmailVerificationRequests("not-json"))
                .isInstanceOf(EventDecodingException.class);

        verify(workerService, never()).sendEmailVerificationMail(any());
    }

    @Test
    void bareEventWithoutEnvelope_isRejected() {
        String legacy = "{\"email\":\"alice@example.com\",\"verificationLink\":\"https://cadence.test/verify?t=abc\"}";

        assertThatThrownBy(() -> consumer.listenToEmailVerificationRequests(legacy))
                .isInstanceOf(EventDecodingException.class);
        verify(workerService, never()).sendEmailVerificationMail(any());
    }
}
