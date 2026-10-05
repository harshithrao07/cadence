package com.cadence.auth_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.LikedSongsCreatedEvent;
import com.cadence.events.LikedSongsFailedEvent;
import com.cadence.events.Topics;
import com.cadence.events.UserRegisteredEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.kafka.StringKafkaSender;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Stands in for playlist-service in auth's integration tests: answers auth.user-registered over real Kafka, so the
 * registration saga runs end to end (outbox, relay, listeners). Replies keep the request's sagaId, as the real
 * participant does.
 */
@TestComponent
public class FakePlaylistParticipant {

    public enum Mode { SUCCEED, FAIL, IGNORE }

    /** Tests that change this must reset it to SUCCEED. */
    public static volatile Mode mode = Mode.SUCCEED;

    private final EventCodec codec;
    private final StringKafkaSender sender;

    public FakePlaylistParticipant(EventCodec codec, StringKafkaSender sender) {
        this.codec = codec;
        this.sender = sender;
    }

    @KafkaListener(topics = Topics.USER_REGISTERED_TOPIC, groupId = "fake-playlist-participant")
    void onUserRegistered(String message) {
        EventEnvelope<UserRegisteredEvent> request = codec.decode(message, UserRegisteredEvent.class);
        String userId = request.payload().userId();
        switch (mode) {
            case SUCCEED -> sender.template().send(Topics.LIKED_SONGS_CREATED_TOPIC, userId, codec.encode(EventEnvelope.of(
                    request.sagaId(), "LikedSongsCreatedEvent", new LikedSongsCreatedEvent(userId, "LIKED_SONGS_" + userId))));
            case FAIL -> sender.template().send(Topics.LIKED_SONGS_FAILED_TOPIC, userId, codec.encode(EventEnvelope.of(
                    request.sagaId(), "LikedSongsFailedEvent", new LikedSongsFailedEvent(userId, "test failure"))));
            case IGNORE -> { }
        }
    }
}
