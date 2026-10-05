package com.cadence.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Wrapper every saga event travels in (adopted on the wire in Phase 1).
 *
 * @param eventId    unique per event; consumers dedupe on it
 * @param sagaId     correlates every event of one saga instance
 * @param type       event type, e.g. "UserRegistered"
 * @param version    payload schema version
 * @param occurredAt when the producing transaction committed the event
 * @param payload    the event body
 */
public record EventEnvelope<T>(
        UUID eventId,
        UUID sagaId,
        String type,
        int version,
        Instant occurredAt,
        T payload
) {
    public static <T> EventEnvelope<T> of(UUID sagaId, String type, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), sagaId, type, 1, Instant.now(), payload);
    }
}
