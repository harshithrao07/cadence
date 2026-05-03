package com.cadence.notification_service.consumers;

import com.cadence.notification_service.events.EmailVerificationEvent;
import com.cadence.notification_service.services.WorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationConsumerTest {

    @Mock WorkerService workerService;
    @org.mockito.Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks EmailVerificationConsumer consumer;

    @Test
    void deserializesPayload_andDelegatesToWorker() throws Exception {
        String payload = "{\"email\":\"alice@example.com\",\"verificationLink\":\"https://cadence.test/verify?t=abc\"}";

        consumer.listenToEmailVerificationRequests(payload);

        ArgumentCaptor<EmailVerificationEvent> captor = ArgumentCaptor.forClass(EmailVerificationEvent.class);
        verify(workerService).sendEmailVerificationMail(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().getVerificationLink()).isEqualTo("https://cadence.test/verify?t=abc");
    }

    @Test
    void invalidPayload_throws_andDoesNotInvokeWorker() {
        assertThatThrownBy(() -> consumer.listenToEmailVerificationRequests("not-json"))
                .isInstanceOf(Exception.class);

        verify(workerService, never()).sendEmailVerificationMail(any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
