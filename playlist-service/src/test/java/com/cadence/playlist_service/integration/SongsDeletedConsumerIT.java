package com.cadence.playlist_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.SongsDeletedEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.playlist_service.consumers.SongsDeletedConsumer;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.model.PlaylistVisibility;
import com.cadence.playlist_service.repository.LikedPlaylistRepository;
import com.cadence.playlist_service.repository.PlaylistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SongsDeletedConsumerIT extends BaseIntegrationTest {

    @Autowired SongsDeletedConsumer consumer;
    @Autowired PlaylistRepository playlistRepository;
    @Autowired LikedPlaylistRepository likedPlaylistRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EventCodec codec;

    private String mixId;
    private String likedId;
    private String untouchedId;

    @BeforeEach
    void setUp() {
        likedPlaylistRepository.deleteAll();
        playlistRepository.deleteAll();
        mixId = save("Mix", "owner-1", "s1", "gone-a", "s2", "gone-b", "s3");
        likedId = playlistRepository.save(Playlist.builder()
                .name("Liked Songs").ownerId("owner-2").isSystem(true)
                .systemType(com.cadence.playlist_service.model.SystemPlaylistType.LIKED_SONGS)
                .visibility(PlaylistVisibility.PRIVATE)
                .songIds(new ArrayList<>(List.of("gone-a", "s9")))
                .build()).getId();
        untouchedId = save("Other", "owner-3", "s4", "s5");
    }

    @Test
    void deletedSongs_areRemovedEverywhere_andRemainingOrderIsCompact() {
        consumer.handleSongsDeleted(event("gone-a", "gone-b"));

        assertThat(songs(mixId)).containsExactly("s1", "s2", "s3");
        assertThat(songs(likedId)).containsExactly("s9");
        assertThat(songs(untouchedId)).containsExactly("s4", "s5");
        // No gaps left in song_order (a gap would load as a null element).
        assertThat(jdbcTemplate.queryForList(
                "SELECT song_order FROM playlist_songs WHERE playlist_id = ? ORDER BY song_order", Integer.class, mixId))
                .containsExactly(0, 1, 2);
    }

    @Test
    void redelivery_andUnknownSongs_areHarmless() {
        String message = event("gone-a", "never-existed");
        consumer.handleSongsDeleted(message);
        consumer.handleSongsDeleted(message);

        assertThat(songs(mixId)).containsExactly("s1", "s2", "gone-b", "s3");
        assertThat(songs(untouchedId)).containsExactly("s4", "s5");
    }

    private String save(String name, String owner, String... songIds) {
        return playlistRepository.save(Playlist.builder()
                .name(name).ownerId(owner).visibility(PlaylistVisibility.PUBLIC)
                .songIds(new ArrayList<>(List.of(songIds)))
                .build()).getId();
    }

    private List<String> songs(String playlistId) {
        return jdbcTemplate.queryForList(
                "SELECT song_id FROM playlist_songs WHERE playlist_id = ? ORDER BY song_order", String.class, playlistId);
    }

    private String event(String... songIds) {
        return codec.encode(EventEnvelope.of(UUID.randomUUID(), "SongsDeletedEvent",
                new SongsDeletedEvent("record-1", List.of(songIds))));
    }
}
