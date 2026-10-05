package com.cadence.playlist_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.UserCreatedEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.EventDecodingException;
import com.cadence.playlist_service.consumers.UserCreatedConsumer;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls the listener method directly (listeners don't start in tests) with envelope JSON as Kafka delivers it.
 */
class UserCreatedConsumerIT extends BaseIntegrationTest {

    @Autowired UserCreatedConsumer consumer;
    @Autowired EventCodec codec;
    @Autowired PlaylistRepository playlistRepository;
    @Autowired LikedPlaylistRepository likedPlaylistRepository;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        likedPlaylistRepository.deleteAll();
        playlistRepository.deleteAll();
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                entityManager.createQuery("DELETE FROM ProcessedEvent").executeUpdate());
    }

    @Test
    void userCreated_createsLikedSongs_onceEvenIfDeliveredTwice() {
        String message = codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserCreatedEvent", new UserCreatedEvent("user-1")));

        consumer.handleUserCreated(message);
        consumer.handleUserCreated(message);

        assertThat(playlistRepository.findAll())
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.getId()).isEqualTo(SystemPlaylistType.LIKED_SONGS.name() + "_user-1");
                    assertThat(p.getOwnerId()).isEqualTo("user-1");
                    assertThat(p.isSystem()).isTrue();
                });
        assertThat(processedCount()).isEqualTo(1);
    }

    @Test
    void distinctEvents_forSameUser_stillLeaveOnePlaylist() {
        consumer.handleUserCreated(codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserCreatedEvent", new UserCreatedEvent("user-1"))));
        consumer.handleUserCreated(codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserCreatedEvent", new UserCreatedEvent("user-1"))));

        assertThat(playlistRepository.findAll()).extracting(Playlist::getOwnerId).containsExactly("user-1");
        assertThat(processedCount()).isEqualTo(2);
    }

    @Test
    void malformedMessage_isRejectedWithoutSideEffects() {
        assertThatThrownBy(() -> consumer.handleUserCreated("{\"userId\":\"user-1\"}"))
                .isInstanceOf(EventDecodingException.class);

        assertThat(playlistRepository.findAll()).isEmpty();
        assertThat(processedCount()).isZero();
    }

    private long processedCount() {
        return new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT COUNT(e) FROM ProcessedEvent e", Long.class)
                .getSingleResult());
    }
}
