package com.cadence.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One event waiting to be (or already) relayed to Kafka. Written in the same transaction as the business
 * change. Column layout follows the Debezium outbox event router (aggregate type / id, type, payload), with an
 * explicit topic column so a CDC connector can route on it instead of the polling relay.
 */
@Entity
@Table(
        name = "outbox",
        indexes = {
                @Index(name = "idx_outbox_sent_at_id", columnList = "sent_at, id")
        }
)
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "saga_id", nullable = false, length = 36)
    private String sagaId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(nullable = false, length = 128)
    private String type;

    @Column(nullable = false, length = 128)
    private String topic;

    /**
     * The full envelope JSON, sent to Kafka as-is. Explicit length: Hibernate otherwise sizes a {@code @Lob}
     * string at 255, which MySQL maps to TINYTEXT. This yields MEDIUMTEXT (16 MB).
     */
    @Lob
    @Column(nullable = false, length = 16_777_215)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Set when Kafka rejected the row permanently (e.g. invalid topic); the relay skips it from then on. */
    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventId, String sagaId, String aggregateType, String aggregateId, String type,
                       String topic, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.sagaId = sagaId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSagaId() {
        return sagaId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void markSent(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public void markFailed(Instant failedAt, String error) {
        this.failedAt = failedAt;
        this.lastError = error == null || error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
