package com.cadence.notification_service.consumers;

import com.cadence.notification_service.events.RecordCreatedEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordCreatedConsumerTest {

    @Mock WorkerService workerService;
    @org.mockito.Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks RecordCreatedConsumer consumer;

    @Test
    void deserializesPayload_andDelegatesToWorker() throws Exception {
        String payload = "{"
                + "\"recordId\":\"rec-1\","
                + "\"recordTitle\":\"Certified Lover Boy\","
                + "\"artists\":[\"Drake\"],"
                + "\"coverUrl\":\"https://cdn/cover.jpg\","
                + "\"followerEmails\":[\"alice@example.com\",\"bob@example.com\"]"
                + "}";

        consumer.listenToNewlyCreatedRecord(payload);

        ArgumentCaptor<RecordCreatedEvent> captor = ArgumentCaptor.forClass(RecordCreatedEvent.class);
        verify(workerService).notifyFollowersOfNewRelease(captor.capture());
        assertThat(captor.getValue().getRecordId()).isEqualTo("rec-1");
        assertThat(captor.getValue().getFollowerEmails()).containsExactly("alice@example.com", "bob@example.com");
    }

    @Test
    void invalidPayload_throws_andDoesNotInvokeWorker() {
        assertThatThrownBy(() -> consumer.listenToNewlyCreatedRecord("not-json"))
                .isInstanceOf(Exception.class);

        verify(workerService, never()).notifyFollowersOfNewRelease(any());
    }
}
