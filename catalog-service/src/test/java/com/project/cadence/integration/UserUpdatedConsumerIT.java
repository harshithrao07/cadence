package com.project.cadence.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.UserUpdatedEvent;
import com.cadence.messaging.EventCodec;
import com.project.cadence.consumers.UserUpdatedConsumer;
import com.project.cadence.model.UserReplica;
import com.project.cadence.repository.UserReplicaRepository;
import com.project.cadence.service.AwsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the listener directly with envelope JSON as Kafka delivers it.
 */
class UserUpdatedConsumerIT extends BaseIntegrationTest {

    @Autowired UserUpdatedConsumer consumer;
    @Autowired UserReplicaRepository userReplicaRepository;
    @Autowired EventCodec codec;

    @MockBean AwsService awsService;

    @BeforeEach
    void setUp() {
        userReplicaRepository.deleteAll();
    }

    @Test
    void snapshot_isInserted_thenReplacedByNewerOne() {
        consumer.handleUserUpdated(message(new UserUpdatedEvent("u-1", "Alice", "alice@example.com", null), Instant.now().minusSeconds(10)));
        consumer.handleUserUpdated(message(new UserUpdatedEvent("u-1", "Alice B", "alice@example.com", "https://cdn/a"), Instant.now()));

        UserReplica replica = userReplicaRepository.findById("u-1").orElseThrow();
        assertThat(replica.getName()).isEqualTo("Alice B");
        assertThat(replica.getEmail()).isEqualTo("alice@example.com");
        assertThat(replica.getProfileUrl()).isEqualTo("https://cdn/a");
    }

    @Test
    void olderSnapshot_arrivingLate_isIgnored() {
        consumer.handleUserUpdated(message(new UserUpdatedEvent("u-1", "Newer", null, null), Instant.now()));
        consumer.handleUserUpdated(message(new UserUpdatedEvent("u-1", "Older", null, null), Instant.now().minusSeconds(60)));

        assertThat(userReplicaRepository.findById("u-1").orElseThrow().getName()).isEqualTo("Newer");
    }

    @Test
    void redelivery_isApplied_once() {
        String message = message(new UserUpdatedEvent("u-1", "Alice", null, null), Instant.now());
        consumer.handleUserUpdated(message);
        consumer.handleUserUpdated(message);

        assertThat(userReplicaRepository.count()).isEqualTo(1);
    }

    private String message(UserUpdatedEvent event, Instant occurredAt) {
        return codec.encode(new EventEnvelope<>(UUID.randomUUID(), UUID.randomUUID(), "UserUpdatedEvent", 1, occurredAt, event));
    }
}
