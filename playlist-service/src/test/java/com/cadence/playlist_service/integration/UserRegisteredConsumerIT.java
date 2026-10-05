package com.cadence.playlist_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.LikedSongsCreatedEvent;
import com.cadence.events.Topics;
import com.cadence.events.UserRegisteredEvent;
import com.cadence.messaging.outbox.OutboxEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.EventDecodingException;
import com.cadence.playlist_service.consumers.UserRegisteredConsumer;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.model.SystemPlaylistType;
import com.cadence.playlist_service.repository.LikedPlaylistRepository;
import com.cadence.playlist_service.repository.PlaylistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls the listener method directly (listeners don't start in tests) with envelope JSON as Kafka delivers it.
 */
class UserRegisteredConsumerIT extends BaseIntegrationTest {

    @Autowired UserRegisteredConsumer consumer;
    @Autowired EventCodec codec;
    @Autowired PlaylistRepository playlistRepository;
    @Autowired LikedPlaylistRepository likedPlaylistRepository;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        likedPlaylistRepository.deleteAll();
        playlistRepository.deleteAll();
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            entityManager.createQuery("DELETE FROM ProcessedEvent").executeUpdate();
            entityManager.createQuery("DELETE FROM OutboxEvent").executeUpdate();
        });
    }

    @Test
    void userRegistered_createsLikedSongs_andRepliesOnce_evenIfDeliveredTwice() {
        UUID sagaId = UUID.randomUUID();
        String message = codec.encode(EventEnvelope.of(sagaId, "UserRegisteredEvent", new UserRegisteredEvent("user-1")));

        consumer.handleUserRegistered(message);
        consumer.handleUserRegistered(message);

        assertThat(playlistRepository.findAll())
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.getId()).isEqualTo(SystemPlaylistType.LIKED_SONGS.name() + "_user-1");
                    assertThat(p.getOwnerId()).isEqualTo("user-1");
                    assertThat(p.isSystem()).isTrue();
                });
        assertThat(processedCount()).isEqualTo(1);

        List<OutboxEvent> replies = outbox(Topics.LIKED_SONGS_CREATED_TOPIC);
        assertThat(replies).singleElement().satisfies(reply -> {
            assertThat(reply.getAggregateId()).isEqualTo("user-1");
            assertThat(reply.getSagaId()).as("reply joins the registration saga").isEqualTo(sagaId.toString());
            assertThat(codec.decode(reply.getPayload(), LikedSongsCreatedEvent.class).payload())
                    .isEqualTo(new LikedSongsCreatedEvent("user-1", SystemPlaylistType.LIKED_SONGS.name() + "_user-1"));
        });
    }

    @Test
    void registrationWithoutUserId_repliesFailed_andCreatesNothing() {
        consumer.handleUserRegistered(codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserRegisteredEvent", new UserRegisteredEvent(" "))));

        assertThat(playlistRepository.findAll()).isEmpty();
        assertThat(outbox(Topics.LIKED_SONGS_CREATED_TOPIC)).isEmpty();
        assertThat(outbox(Topics.LIKED_SONGS_FAILED_TOPIC)).hasSize(1);
    }

    @Test
    void distinctEvents_forSameUser_stillLeaveOnePlaylist() {
        consumer.handleUserRegistered(codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserRegisteredEvent", new UserRegisteredEvent("user-1"))));
        consumer.handleUserRegistered(codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserRegisteredEvent", new UserRegisteredEvent("user-1"))));

        assertThat(playlistRepository.findAll()).extracting(Playlist::getOwnerId).containsExactly("user-1");
        assertThat(processedCount()).isEqualTo(2);
    }

    @Test
    void malformedMessage_isRejectedWithoutSideEffects() {
        assertThatThrownBy(() -> consumer.handleUserRegistered("{\"userId\":\"user-1\"}"))
                .isInstanceOf(EventDecodingException.class);

        assertThat(playlistRepository.findAll()).isEmpty();
        assertThat(processedCount()).isZero();
    }

    private List<OutboxEvent> outbox(String topic) {
        return new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT e FROM OutboxEvent e WHERE e.topic = :topic", OutboxEvent.class)
                .setParameter("topic", topic)
                .getResultList());
    }

    private long processedCount() {
        return new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT COUNT(e) FROM ProcessedEvent e", Long.class)
                .getSingleResult());
    }
}
