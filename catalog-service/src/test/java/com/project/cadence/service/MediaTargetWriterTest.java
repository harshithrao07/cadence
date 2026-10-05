package com.project.cadence.service;

import com.cadence.events.MediaUpdatedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.outbox.OutboxPublisher;
import com.project.cadence.constant.UploadTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MediaTargetWriterTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock OutboxPublisher outboxPublisher;
    @InjectMocks MediaTargetWriter writer;

    @Test
    void catalogOwnedTarget_updatesCatalogRowDirectly() {
        writer.write(UploadTarget.RECORD_COVER, "r-1", "https://cdn/r-1", "admin-1", true);

        verify(jdbcTemplate).update("UPDATE record SET cover_url = ? WHERE id = ?", "https://cdn/r-1", "r-1");
        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void userAvatar_isPublishedForAuthService_notWrittenHere() {
        writer.write(UploadTarget.USER_AVATAR, "u-1", "https://cdn/u-1", "u-1", false);

        verify(outboxPublisher).publish(Topics.MEDIA_UPDATED_TOPIC, "user", "u-1",
                new MediaUpdatedEvent(MediaUpdatedEvent.Target.USER_AVATAR, "u-1", "https://cdn/u-1", "u-1", false));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void playlistCoverRemoval_isPublishedWithNullUrl() {
        writer.write(UploadTarget.PLAYLIST_COVER, "p-1", null, "u-1", false);

        verify(outboxPublisher).publish(Topics.MEDIA_UPDATED_TOPIC, "playlist", "p-1",
                new MediaUpdatedEvent(MediaUpdatedEvent.Target.PLAYLIST_COVER, "p-1", null, "u-1", false));
        verifyNoInteractions(jdbcTemplate);
    }
}
