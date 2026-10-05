package com.cadence.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Marks that a handler has applied an event. Inserted in the same transaction as the handler's changes, so the
 * event counts as processed exactly when its effects are committed.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @EmbeddedId
    private Key id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(Key id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }

    public Key getId() {
        return id;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "handler", nullable = false, length = 100)
        private String handler;

        @Column(name = "event_id", nullable = false, length = 36)
        private String eventId;

        protected Key() {
        }

        public Key(String handler, String eventId) {
            this.handler = handler;
            this.eventId = eventId;
        }

        public String getHandler() {
            return handler;
        }

        public String getEventId() {
            return eventId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return handler.equals(key.handler) && eventId.equals(key.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(handler, eventId);
        }
    }
}
