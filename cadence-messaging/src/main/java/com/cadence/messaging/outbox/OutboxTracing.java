package com.cadence.messaging.outbox;

import org.apache.kafka.common.header.Headers;

/**
 * Carries a trace across the outbox: the publisher saves the current trace context on the row, and the relay
 * continues that trace when it sends the row, so a saga shows up as one trace from the originating request.
 * {@link #NOOP} is used when the service has no tracer.
 */
public interface OutboxTracing {

    OutboxTracing NOOP = new OutboxTracing() {
        @Override
        public String currentTraceparent() {
            return null;
        }

        @Override
        public SendSpan startSend(String traceparent, String topic, Headers headers) {
            return SendSpan.NOOP;
        }
    };

    /**
     * @return the W3C {@code traceparent} of the current span, or {@code null} when there is none
     */
    String currentTraceparent();

    /**
     * Starts a producer span continuing {@code traceparent} and writes its propagation headers into
     * {@code headers}, so the consumer joins the same trace. Does nothing when {@code traceparent} is null.
     */
    SendSpan startSend(String traceparent, String topic, Headers headers);

    interface SendSpan extends AutoCloseable {
        SendSpan NOOP = new SendSpan() {
            @Override
            public void error(Throwable error) {
            }

            @Override
            public void close() {
            }
        };

        void error(Throwable error);

        @Override
        void close();
    }
}
