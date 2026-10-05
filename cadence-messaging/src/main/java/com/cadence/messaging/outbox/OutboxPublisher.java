package com.cadence.messaging.outbox;

import java.util.UUID;

/**
 * Records an event to be published. Must be called inside the transaction that makes the business change, so
 * the event is stored if and only if that change commits. Delivery to Kafka happens later, at least once.
 */
public interface OutboxPublisher {

    /**
     * @param topic         Kafka topic
     * @param aggregateType kind of entity the event is about, e.g. "user"
     * @param aggregateId   id of that entity; used as the Kafka key, so events for one aggregate stay ordered
     * @param payload       event body; its simple class name becomes the envelope type
     * @return the event id
     */
    UUID publish(String topic, String aggregateType, String aggregateId, Object payload);
}
