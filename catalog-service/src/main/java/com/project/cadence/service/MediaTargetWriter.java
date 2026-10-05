package com.project.cadence.service;

import com.cadence.events.MediaUpdatedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.outbox.OutboxPublisher;
import com.project.cadence.constant.UploadTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a stored (or removed, url = null) file against its target row: catalog's own tables are updated
 * directly; rows owned by another service get a {@link MediaUpdatedEvent} for that service to apply.
 */
@Service
@RequiredArgsConstructor
public class MediaTargetWriter {
    private final JdbcTemplate jdbcTemplate;
    private final OutboxPublisher outboxPublisher;

    @Transactional
    public void write(UploadTarget target, String primaryKey, String url, String userId, boolean isAdmin) {
        MediaUpdatedEvent.Target external = target.externalTarget();
        if (external == null) {
            jdbcTemplate.update(target.updateSql(), url, primaryKey);
            return;
        }
        String aggregateType = external == MediaUpdatedEvent.Target.USER_AVATAR ? "user" : "playlist";
        outboxPublisher.publish(Topics.MEDIA_UPDATED_TOPIC, aggregateType, primaryKey,
                new MediaUpdatedEvent(external, primaryKey, url, userId, isAdmin));
    }
}
