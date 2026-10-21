package com.cadence.messaging.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link OutboxTracing} backed by Micrometer Tracing. Assumes W3C propagation (Spring Boot's default).
 */
public class MicrometerOutboxTracing implements OutboxTracing {
    static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public MicrometerOutboxTracing(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    @Override
    public SendSpan startSend(String traceparent, String topic, Headers headers) {
        if (traceparent == null) {
            return SendSpan.NOOP;
        }
        Span span = propagator.extract(Map.of(TRACEPARENT, traceparent), Map::get)
                .name(topic + " send")
                .kind(Span.Kind.PRODUCER)
                .tag("messaging.system", "kafka")
                .tag("messaging.destination.name", topic)
                .start();
        propagator.inject(span.context(), headers,
                (carrier, key, value) -> carrier.add(key, value.getBytes(StandardCharsets.UTF_8)));
        return new SendSpan() {
            @Override
            public void error(Throwable error) {
                span.error(error);
            }

            @Override
            public void close() {
                span.end();
            }
        };
    }
}
