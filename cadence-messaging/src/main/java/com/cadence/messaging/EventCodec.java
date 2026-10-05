package com.cadence.messaging;

import com.cadence.events.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Serializes events into {@link EventEnvelope} JSON and back. Every Cadence topic carries envelope JSON
 * as a plain string value.
 * <p>
 * Uses its own mapper rather than the service's: the envelope is a contract between services and must not change
 * with one service's Jackson customization. Unknown fields are ignored so producers can add fields to an event
 * before every consumer is upgraded.
 */
public class EventCodec {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public String encode(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize event " + envelope.type(), e);
        }
    }

    public <T> EventEnvelope<T> decode(String message, Class<T> payloadType) {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType);
        try {
            EventEnvelope<T> envelope = objectMapper.readValue(message, type);
            if (envelope.eventId() == null || envelope.payload() == null) {
                throw new EventDecodingException("Envelope is missing eventId or payload", null);
            }
            return envelope;
        } catch (JsonProcessingException e) {
            throw new EventDecodingException("Could not parse " + payloadType.getSimpleName() + " envelope", e);
        }
    }
}
