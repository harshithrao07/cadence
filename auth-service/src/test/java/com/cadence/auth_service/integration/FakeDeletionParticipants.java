package com.cadence.auth_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.Topics;
import com.cadence.events.UserDataPurgedEvent;
import com.cadence.events.UserDeletionRequestedEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.kafka.StringKafkaSender;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.Map;
import java.util.Set;

/**
 * Stands in for playlist, catalog and streaming in auth's integration tests: answers auth.user-deletion-requested
 * with each service's purge confirmation over real Kafka, keeping the request's sagaId.
 */
@TestComponent
public class FakeDeletionParticipants {

    private static final Map<String, String> TOPIC_BY_SERVICE = Map.of(
            "playlist", Topics.PLAYLIST_USER_DATA_PURGED_TOPIC,
            "catalog", Topics.CATALOG_USER_DATA_PURGED_TOPIC,
            "streaming", Topics.STREAMING_USER_DATA_PURGED_TOPIC);

    /** Services that don't answer. Tests that change this must reset it to empty. */
    public static volatile Set<String> silent = Set.of();

    private final EventCodec codec;
    private final StringKafkaSender sender;

    public FakeDeletionParticipants(EventCodec codec, StringKafkaSender sender) {
        this.codec = codec;
        this.sender = sender;
    }

    @KafkaListener(topics = Topics.USER_DELETION_REQUESTED_TOPIC, groupId = "fake-deletion-participants")
    void onDeletionRequested(String message) {
        EventEnvelope<UserDeletionRequestedEvent> request = codec.decode(message, UserDeletionRequestedEvent.class);
        String userId = request.payload().userId();
        TOPIC_BY_SERVICE.forEach((service, topic) -> {
            if (!silent.contains(service)) {
                sender.template().send(topic, userId, codec.encode(EventEnvelope.of(
                        request.sagaId(), "UserDataPurgedEvent", new UserDataPurgedEvent(userId, service))));
            }
        });
    }
}
