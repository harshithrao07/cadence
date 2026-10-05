package com.cadence.messaging.outbox;

import com.cadence.events.EventEnvelope;
import com.cadence.messaging.EventCodec;
import com.cadence.messaging.SagaContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class JpaOutboxPublisher implements OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(JpaOutboxPublisher.class);

    private final EventCodec codec;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaOutboxPublisher(EventCodec codec) {
        this.codec = codec;
    }

    /**
     * MANDATORY: publishing outside a transaction would break atomicity, so it fails fast. Any failure here also
     * marks the caller's transaction rollback-only, so the business change can't commit without its event even if
     * the caller catches the exception.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(String topic, String aggregateType, String aggregateId, Object payload) {
        UUID sagaId = SagaContext.currentSagaId().orElseGet(UUID::randomUUID);
        EventEnvelope<Object> envelope = EventEnvelope.of(sagaId, payload.getClass().getSimpleName(), payload);

        entityManager.persist(new OutboxEvent(
                envelope.eventId().toString(),
                sagaId.toString(),
                aggregateType,
                aggregateId,
                envelope.type(),
                topic,
                codec.encode(envelope),
                envelope.occurredAt()
        ));

        log.info("Recorded {} for {} {} in outbox (topic={}, eventId={}, sagaId={})",
                envelope.type(), aggregateType, aggregateId, topic, envelope.eventId(), sagaId);
        return envelope.eventId();
    }
}
