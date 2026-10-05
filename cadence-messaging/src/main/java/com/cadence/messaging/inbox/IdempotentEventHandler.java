package com.cadence.messaging.inbox;

import com.cadence.events.EventEnvelope;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.SagaContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Runs a Kafka listener's work at most once per event:
 * <ol>
 *     <li>decodes the envelope (malformed messages throw {@link com.cadence.messaging.EventDecodingException},
 *     which goes straight to the DLT),</li>
 *     <li>joins the event's saga (MDC + events published by the action carry the same sagaId),</li>
 *     <li>skips the event if this handler already processed it, otherwise records it and runs the action,
 *     all in one transaction.</li>
 * </ol>
 * If the action throws, everything rolls back (including the processed marker) and the error handler retries.
 * Two concurrent deliveries of one event collide on the primary key; the loser rolls back and, on retry, sees
 * the event as processed.
 */
public class IdempotentEventHandler {
    private static final Logger log = LoggerFactory.getLogger(IdempotentEventHandler.class);

    private final EventCodec codec;

    @PersistenceContext
    private EntityManager entityManager;

    public IdempotentEventHandler(EventCodec codec) {
        this.codec = codec;
    }

    /**
     * @param handler     stable name of this handler, e.g. "playlist.create-liked-songs". Several handlers may
     *                    process the same event independently.
     * @param message     the raw Kafka value
     * @param payloadType the event class
     * @param action      the work; runs inside this method's transaction
     * @return true if the action ran, false if the event was a duplicate
     */
    @Transactional
    public <T> boolean handle(String handler, String message, Class<T> payloadType, Consumer<EventEnvelope<T>> action) {
        EventEnvelope<T> envelope = codec.decode(message, payloadType);

        try (SagaContext.Scope ignored = SagaContext.join(envelope.sagaId(), envelope.eventId())) {
            ProcessedEvent.Key key = new ProcessedEvent.Key(handler, envelope.eventId().toString());
            if (entityManager.find(ProcessedEvent.class, key) != null) {
                log.info("Skipping duplicate {} (eventId={}) for {}", envelope.type(), envelope.eventId(), handler);
                return false;
            }

            entityManager.persist(new ProcessedEvent(key, Instant.now()));
            entityManager.flush();

            action.accept(envelope);
            log.info("Handled {} (eventId={}) with {}", envelope.type(), envelope.eventId(), handler);
            return true;
        }
    }
}
