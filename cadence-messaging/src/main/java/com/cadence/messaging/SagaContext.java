package com.cadence.messaging;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * Carries the saga id of the event currently being handled on this thread, so events published while
 * handling it join the same saga, and puts {@code sagaId} / {@code eventId} into the logging MDC.
 */
public final class SagaContext {
    public static final String SAGA_ID_MDC_KEY = "sagaId";
    public static final String EVENT_ID_MDC_KEY = "eventId";

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private SagaContext() {
    }

    public static Optional<UUID> currentSagaId() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Joins the given saga until the returned scope is closed; the previous context is restored on close.
     */
    public static Scope join(UUID sagaId, UUID eventId) {
        UUID previousSaga = CURRENT.get();
        String previousSagaMdc = MDC.get(SAGA_ID_MDC_KEY);
        String previousEventMdc = MDC.get(EVENT_ID_MDC_KEY);

        CURRENT.set(sagaId);
        putOrRemove(SAGA_ID_MDC_KEY, sagaId == null ? null : sagaId.toString());
        putOrRemove(EVENT_ID_MDC_KEY, eventId == null ? null : eventId.toString());

        return () -> {
            if (previousSaga == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previousSaga);
            }
            putOrRemove(SAGA_ID_MDC_KEY, previousSagaMdc);
            putOrRemove(EVENT_ID_MDC_KEY, previousEventMdc);
        };
    }

    private static void putOrRemove(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
