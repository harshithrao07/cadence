package com.cadence.notification_service.consumers;

import com.cadence.events.EventEnvelope;
import com.cadence.events.RecordCreatedEvent;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordCreatedConsumerTest {

    @Mock WorkerService workerService;
    @Spy EventCodec codec = new EventCodec();

    @InjectMocks RecordCreatedConsumer consumer;

    @Test
    void decodesEnvelope_andDelegatesToWorker() {
        RecordCreatedEvent event = new RecordCreatedEvent("rec-1", "Certified Lover Boy", List.of("Drake"),
                "https://cdn/cover.jpg", List.of("alice@example.com", "bob@example.com"));

        consumer.listenToNewlyCreatedRecord(codec.encode(EventEnvelope.of(UUID.randomUUID(), "RecordCreatedEvent", event)));

        ArgumentCaptor<RecordCreatedEvent> captor = ArgumentCaptor.forClass(RecordCreatedEvent.class);
        verify(workerService).notifyFollowersOfNewRelease(captor.capture());
        assertThat(captor.getValue().recordId()).isEqualTo("rec-1");
        assertThat(captor.getValue().followerEmails()).containsExactly("alice@example.com", "bob@example.com");
    }

    @Test
    void invalidPayload_throws_andDoesNotInvokeWorker() {
        assertThatThrownBy(() -> consumer.listenToNewlyCreatedRecord("not-json"))
                .isInstanceOf(EventDecodingException.class);

        verify(workerService, never()).notifyFollowersOfNewRelease(any());
    }
}
