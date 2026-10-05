package com.cadence.playlist_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.Topics;
import com.cadence.events.UserDataPurgedEvent;
import com.cadence.events.UserDeletionRequestedEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.outbox.OutboxEvent;
import com.cadence.playlist_service.consumers.UserDeletionRequestedConsumer;
import com.cadence.playlist_service.model.LikedPlaylist;
import com.cadence.playlist_service.model.LikedPlaylistId;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.model.PlaylistVisibility;
import com.cadence.playlist_service.model.SystemPlaylistType;
import com.cadence.playlist_service.repository.LikedPlaylistRepository;
import com.cadence.playlist_service.repository.PlaylistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDeletionRequestedConsumerIT extends BaseIntegrationTest {

    @Autowired UserDeletionRequestedConsumer consumer;
    @Autowired PlaylistRepository playlistRepository;
    @Autowired LikedPlaylistRepository likedPlaylistRepository;
    @Autowired EventCodec codec;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    private String gonePlaylist;
    private String keptPlaylist;

    @BeforeEach
    void setUp() {
        likedPlaylistRepository.deleteAll();
        playlistRepository.deleteAll();
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            entityManager.createQuery("DELETE FROM OutboxEvent").executeUpdate();
            entityManager.createQuery("DELETE FROM ProcessedEvent").executeUpdate();
        });
        gonePlaylist = playlistRepository.save(Playlist.builder().name("Theirs").ownerId("leaving")
                .visibility(PlaylistVisibility.PUBLIC).songIds(new ArrayList<>(List.of("s1", "s2"))).build()).getId();
        playlistRepository.save(Playlist.builder().name("Liked Songs").ownerId("leaving").isSystem(true)
                .systemType(SystemPlaylistType.LIKED_SONGS).visibility(PlaylistVisibility.PRIVATE).build());
        keptPlaylist = playlistRepository.save(Playlist.builder().name("Mine").ownerId("staying")
                .visibility(PlaylistVisibility.PUBLIC).build()).getId();
        like("leaving", keptPlaylist, 0);   // the leaving user's like on someone else's playlist
        like("staying", gonePlaylist, 0);   // someone else's like on the leaving user's playlist
        like("staying", keptPlaylist, 1);
    }

    @Test
    void purgesPlaylistsAndLikes_inBothDirections_andConfirms() {
        UUID sagaId = UUID.randomUUID();
        String message = codec.encode(EventEnvelope.of(sagaId, "UserDeletionRequestedEvent", new UserDeletionRequestedEvent("leaving")));

        consumer.handleUserDeletionRequested(message);
        consumer.handleUserDeletionRequested(message);

        assertThat(playlistRepository.findAll()).extracting(Playlist::getId).containsExactly(keptPlaylist);
        assertThat(likedPlaylistRepository.findAll()).extracting(LikedPlaylist::getId)
                .containsExactly(new LikedPlaylistId("staying", keptPlaylist));

        List<OutboxEvent> replies = new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT e FROM OutboxEvent e WHERE e.topic = :t", OutboxEvent.class)
                .setParameter("t", Topics.PLAYLIST_USER_DATA_PURGED_TOPIC).getResultList());
        assertThat(replies).as("one confirmation despite the redelivery").singleElement().satisfies(r -> {
            assertThat(r.getSagaId()).isEqualTo(sagaId.toString());
            assertThat(codec.decode(r.getPayload(), UserDataPurgedEvent.class).payload())
                    .isEqualTo(new UserDataPurgedEvent("leaving", "playlist"));
        });
    }

    @Test
    void userWithNoData_stillConfirms() {
        consumer.handleUserDeletionRequested(codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserDeletionRequestedEvent",
                new UserDeletionRequestedEvent("never-had-anything"))));

        assertThat(playlistRepository.count()).isEqualTo(3);
        long replies = new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT COUNT(e) FROM OutboxEvent e WHERE e.topic = :t", Long.class)
                .setParameter("t", Topics.PLAYLIST_USER_DATA_PURGED_TOPIC).getSingleResult());
        assertThat(replies).isEqualTo(1);
    }

    private void like(String userId, String playlistId, int order) {
        likedPlaylistRepository.save(LikedPlaylist.builder().id(new LikedPlaylistId(userId, playlistId)).likeOrder(order).build());
    }
}
