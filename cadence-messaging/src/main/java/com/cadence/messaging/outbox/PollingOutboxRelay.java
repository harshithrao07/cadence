package com.cadence.messaging.outbox;

import com.cadence.messaging.MessagingProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.kafka.common.errors.ApiException;
import org.apache.kafka.common.errors.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Moves unsent outbox rows to Kafka, oldest first. Rows are locked with {@code FOR UPDATE SKIP LOCKED}, so
 * several instances of a service can relay concurrently without sending the same row twice. A crash between the
 * Kafka ack and the commit re-sends the row on the next poll (at-least-once); consumers dedupe on eventId.
 * <p>
 * Failures:
 * <ul>
 *     <li>Retriable (broker down, topic missing, timeout): the row's topic is blocked for the rest of the batch so
 *     later events on that topic never overtake it; other topics keep flowing. The row is retried on the next
 *     poll, indefinitely: a topic that never becomes available needs fixing (create it) or its rows parked
 *     by hand.</li>
 *     <li>Permanent (invalid topic, record too large, ...): the row is parked with {@code failed_at} /
 *     {@code last_error} and skipped, so one bad row can't block the outbox forever. Set {@code failed_at} back to
 *     NULL to retry it.</li>
 * </ul>
 */
public class PollingOutboxRelay implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(PollingOutboxRelay.class);

    private static final String SELECT_UNSENT = """
            SELECT * FROM outbox
            WHERE sent_at IS NULL AND failed_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MessagingProperties.Outbox properties;

    @PersistenceContext
    private EntityManager entityManager;

    private ScheduledExecutorService executor;
    private volatile boolean running;

    public PollingOutboxRelay(KafkaTemplate<String, String> kafkaTemplate,
                              TransactionTemplate transactionTemplate,
                              MessagingProperties.Outbox properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /**
     * Relays one batch.
     *
     * @return number of rows sent
     */
    public int relayBatch() {
        Integer sent = transactionTemplate.execute(status -> {
            @SuppressWarnings("unchecked")
            List<OutboxEvent> batch = entityManager.createNativeQuery(SELECT_UNSENT, OutboxEvent.class)
                    .setParameter("limit", properties.getBatchSize())
                    .getResultList();

            int count = 0;
            Set<String> blockedTopics = new HashSet<>();
            for (OutboxEvent event : batch) {
                if (blockedTopics.contains(event.getTopic())) {
                    continue;
                }
                try {
                    kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                            .get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
                    if (isPermanent(cause)) {
                        log.error("Outbox relay parked {} (eventId={}) for {}: Kafka rejected it permanently: {}",
                                event.getType(), event.getEventId(), event.getTopic(), cause.toString());
                        event.markFailed(Instant.now(), cause.toString());
                        continue;
                    }
                    log.warn("Outbox relay could not send {} (eventId={}) to {}; will retry: {}",
                            event.getType(), event.getEventId(), event.getTopic(), cause.toString());
                    blockedTopics.add(event.getTopic());
                    continue;
                }
                event.markSent(Instant.now());
                count++;
            }
            return count;
        });
        return sent == null ? 0 : sent;
    }

    /**
     * Kafka marks transient errors with {@link RetriableException}; any other Kafka API error won't succeed on retry.
     * Non-Kafka failures (e.g. our own send timeout) are treated as transient.
     */
    public static boolean isPermanent(Throwable cause) {
        return cause instanceof ApiException && !(cause instanceof RetriableException);
    }

    /**
     * Deletes sent rows older than the retention.
     *
     * @return number of rows deleted
     */
    public int deleteSentBefore(Instant cutoff) {
        Integer deleted = transactionTemplate.execute(status -> entityManager
                .createQuery("DELETE FROM OutboxEvent e WHERE e.sentAt IS NOT NULL AND e.sentAt < :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate());
        return deleted == null ? 0 : deleted;
    }

    private void pollSafely() {
        try {
            // Keep draining while batches come back full.
            while (running && relayBatch() == properties.getBatchSize()) {
                // next batch
            }
        } catch (Throwable t) {
            // Never let anything escape: the executor would silently cancel all future polls.
            log.error("Outbox relay poll failed: {}", t.getMessage(), t);
        }
    }

    private void cleanupSafely() {
        try {
            int deleted = deleteSentBefore(Instant.now().minus(properties.getRetention()));
            if (deleted > 0) {
                log.info("Outbox cleanup deleted {} sent rows", deleted);
            }
        } catch (Throwable t) {
            log.error("Outbox cleanup failed: {}", t.getMessage(), t);
        }
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "outbox-relay");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
        long interval = properties.getRelayInterval().toMillis();
        executor.scheduleWithFixedDelay(this::pollSafely, interval, interval, TimeUnit.MILLISECONDS);
        long cleanup = properties.getCleanupInterval().toMillis();
        executor.scheduleWithFixedDelay(this::cleanupSafely, cleanup, cleanup, TimeUnit.MILLISECONDS);
        log.info("Outbox relay started (interval={}, batchSize={})",
                properties.getRelayInterval(), properties.getBatchSize());
    }

    @Override
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isRelayEnabled();
    }
}
