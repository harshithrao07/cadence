package com.cadence.playlist_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.MediaUpdatedEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.playlist_service.consumers.MediaUpdatedConsumer;
import com.cadence.playlist_service.controller.InternalPlaylistController;
import com.cadence.playlist_service.model.Playlist;
import com.cadence.playlist_service.model.PlaylistVisibility;
import com.cadence.playlist_service.repository.LikedPlaylistRepository;
import com.cadence.playlist_service.repository.PlaylistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUpdatedConsumerIT extends BaseIntegrationTest {

    @Autowired MediaUpdatedConsumer consumer;
    @Autowired InternalPlaylistController internalPlaylistController;
    @Autowired PlaylistRepository playlistRepository;
    @Autowired LikedPlaylistRepository likedPlaylistRepository;
    @Autowired EventCodec codec;

    private String playlistId;

    @BeforeEach
    void setUp() {
        likedPlaylistRepository.deleteAll();
        playlistRepository.deleteAll();
        playlistId = playlistRepository.save(Playlist.builder()
                .name("Mine").ownerId("owner-1").visibility(PlaylistVisibility.PUBLIC).build()).getId();
    }

    @Test
    void ownerUpload_setsCover_andRemovalClearsIt() {
        consumer.handleMediaUpdated(cover("https://cdn/p", "owner-1", false));
        assertThat(coverUrl()).isEqualTo("https://cdn/p");

        consumer.handleMediaUpdated(cover(null, "owner-1", false));
        assertThat(coverUrl()).isNull();
    }

    @Test
    void nonOwnerUpload_isIgnored() {
        consumer.handleMediaUpdated(cover("https://cdn/evil", "someone-else", false));

        assertThat(coverUrl()).isNull();
    }

    @Test
    void adminUpload_isApplied() {
        consumer.handleMediaUpdated(cover("https://cdn/admin", "admin-1", true));

        assertThat(coverUrl()).isEqualTo("https://cdn/admin");
    }

    @Test
    void avatarEvents_areNotForThisService() {
        consumer.handleMediaUpdated(message(new MediaUpdatedEvent(
                MediaUpdatedEvent.Target.USER_AVATAR, playlistId, "https://cdn/x", "owner-1", false)));

        assertThat(coverUrl()).isNull();
    }

    @Test
    void ownerEndpoint_returnsOwner_or404() {
        assertThat(internalPlaylistController.getOwner(playlistId).getBody()).isEqualTo("owner-1");
        assertThat(internalPlaylistController.getOwner("missing").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String coverUrl() {
        return playlistRepository.findById(playlistId).orElseThrow().getCoverUrl();
    }

    private String cover(String url, String requestedBy, boolean admin) {
        return message(new MediaUpdatedEvent(MediaUpdatedEvent.Target.PLAYLIST_COVER, playlistId, url, requestedBy, admin));
    }

    private String message(MediaUpdatedEvent event) {
        return codec.encode(EventEnvelope.of(UUID.randomUUID(), "MediaUpdatedEvent", event));
    }
}
