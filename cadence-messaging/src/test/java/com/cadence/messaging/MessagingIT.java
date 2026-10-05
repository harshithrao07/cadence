package com.cadence.messaging;

import com.cadence.events.EventEnvelope;
import com.cadence.events.Topics;
import com.cadence.events.UserCreatedEvent;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.messaging.outbox.OutboxEvent;
import com.cadence.messaging.outbox.OutboxPublisher;
import com.cadence.messaging.outbox.PollingOutboxRelay;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MessagingIT {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static {
        MYSQL.start();
        KAFKA.start();
    }

    @Autowired OutboxPublisher publisher;
    @Autowired PollingOutboxRelay relay;
    @Autowired IdempotentEventHandler handler;
    @Autowired EventCodec codec;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired com.cadence.messaging.kafka.StringKafkaSender sender;
    @Autowired MessagingProperties properties;
    @Autowired org.springframework.beans.factory.config.AutowireCapableBeanFactory beanFactory;
    @PersistenceContext EntityManager entityManager;

    TransactionTemplate tx;
    String topic;
    KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            entityManager.createQuery("DELETE FROM OutboxEvent").executeUpdate();
            entityManager.createQuery("DELETE FROM ProcessedEvent").executeUpdate();
        });
        topic = "test." + UUID.randomUUID();
        consumer = consumer(topic);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    // --- outbox ---------------------------------------------------------------------------------------------

    @Test
    void publish_storesEnvelope_andRelaySendsItKeyedByAggregate() {
        UUID eventId = tx.execute(s -> publisher.publish(topic, "user", "user-1", new UserCreatedEvent("user-1")));

        assertThat(unsentCount()).isEqualTo(1);
        assertThat(relay.relayBatch()).isEqualTo(1);
        assertThat(unsentCount()).isZero();

        List<ConsumerRecord<String, String>> records = poll(consumer, 1);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo("user-1");

        EventEnvelope<UserCreatedEvent> envelope = codec.decode(records.get(0).value(), UserCreatedEvent.class);
        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.type()).isEqualTo("UserCreatedEvent");
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.sagaId()).isNotNull();
        assertThat(envelope.payload().userId()).isEqualTo("user-1");
    }

    @Test
    void largePayload_roundTrips() {
        String bigId = "u".repeat(100_000);
        tx.executeWithoutResult(s -> publisher.publish(topic, "user", "user-big", new UserCreatedEvent(bigId)));

        assertThat(relay.relayBatch()).isEqualTo(1);
        assertThat(codec.decode(poll(consumer, 1).get(0).value(), UserCreatedEvent.class).payload().userId())
                .isEqualTo(bigId);
    }

    @Test
    void publish_outsideTransaction_isRejected() {
        assertThatThrownBy(() -> publisher.publish(topic, "user", "user-1", new UserCreatedEvent("user-1")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void publish_inRolledBackTransaction_leavesNothingToSend() {
        tx.executeWithoutResult(s -> {
            publisher.publish(topic, "user", "user-1", new UserCreatedEvent("user-1"));
            s.setRollbackOnly();
        });

        assertThat(unsentCount()).isZero();
    }

    @Test
    void publishFailure_rollsBackCallersTransaction_evenIfCallerSwallowsIt() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            publisher.publish(topic, "user", "user-1", new UserCreatedEvent("user-1"));
            try {
                publisher.publish(topic, "user", "user-2", new Unserializable());
            } catch (RuntimeException swallowed) {
                // caller ignores the failure, like the services' catch-all blocks
            }
        })).isInstanceOf(UnexpectedRollbackException.class);

        assertThat(unsentCount()).isZero();
    }

    @Test
    void publish_insideHandledEvent_joinsThatSaga() {
        UUID sagaId = UUID.randomUUID();
        try (SagaContext.Scope ignored = SagaContext.join(sagaId, UUID.randomUUID())) {
            tx.executeWithoutResult(s -> publisher.publish(topic, "user", "user-1", new UserCreatedEvent("user-1")));
        }
        relay.relayBatch();

        EventEnvelope<UserCreatedEvent> envelope = codec.decode(poll(consumer, 1).get(0).value(), UserCreatedEvent.class);
        assertThat(envelope.sagaId()).isEqualTo(sagaId);
    }

    @Test
    void crashAfterSend_beforeCommit_resendsSameEvent() {
        UUID eventId = tx.execute(s -> publisher.publish(topic, "user", "user-1", new UserCreatedEvent("user-1")));

        // The relay's transaction joins this one, which then rolls back: Kafka got the message, sent_at was lost.
        tx.executeWithoutResult(s -> {
            assertThat(relay.relayBatch()).isEqualTo(1);
            s.setRollbackOnly();
        });
        assertThat(unsentCount()).isEqualTo(1);

        assertThat(relay.relayBatch()).isEqualTo(1);
        assertThat(unsentCount()).isZero();

        List<ConsumerRecord<String, String>> records = poll(consumer, 2);
        assertThat(records).hasSize(2);
        assertThat(records).allSatisfy(r ->
                assertThat(codec.decode(r.value(), UserCreatedEvent.class).eventId()).isEqualTo(eventId));
    }

    @Test
    void concurrentRelays_neverSendTheSameRowTwice() throws Exception {
        int events = 60;
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < events; i++) {
                publisher.publish(topic, "user", "user-" + i, new UserCreatedEvent("user-" + i));
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Callable<Integer>> drains = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                drains.add(() -> {
                    int total = 0;
                    int sent;
                    while ((sent = relay.relayBatch()) > 0) {
                        total += sent;
                    }
                    return total;
                });
            }
            int totalSent = 0;
            for (Future<Integer> f : pool.invokeAll(drains)) {
                totalSent += f.get();
            }
            assertThat(totalSent).isEqualTo(events);
        } finally {
            pool.shutdown();
        }

        List<ConsumerRecord<String, String>> records = poll(consumer, events);
        assertThat(records).hasSize(events);
        assertThat(records.stream().map(r -> codec.decode(r.value(), UserCreatedEvent.class).eventId()).distinct())
                .hasSize(events);
    }

    @Test
    void permanentlyRejectedRow_isParked_andDoesNotBlockLaterEvents() {
        tx.executeWithoutResult(s -> {
            publisher.publish("invalid topic name!", "user", "bad", new UserCreatedEvent("bad"));
            publisher.publish(topic, "user", "good", new UserCreatedEvent("good"));
        });

        relay.relayBatch();

        assertThat(poll(consumer, 1)).extracting(ConsumerRecord::key).containsExactly("good");
        assertThat(unsentCount()).isEqualTo(1);
        OutboxEvent parked = tx.execute(s -> entityManager
                .createQuery("SELECT e FROM OutboxEvent e WHERE e.aggregateId = 'bad'", OutboxEvent.class)
                .getSingleResult());
        assertThat(parked.getFailedAt()).isNotNull();
        assertThat(parked.getLastError()).contains("InvalidTopicException");

        // Parked rows are not retried.
        assertThat(relay.relayBatch()).isZero();
    }

    @Test
    void unavailableTopic_blocksOnlyItsOwnEvents_andKeepsTheirOrder() {
        // A relay whose Kafka times out (retriable) for one topic, as on a broker where the topic can't be created.
        String stuckTopic = "stuck." + UUID.randomUUID();
        KafkaTemplate<String, String> real = sender.template();
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> flaky = org.mockito.Mockito.mock(KafkaTemplate.class);
        org.mockito.Mockito.when(flaky.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            return t.equals(stuckTopic)
                    ? java.util.concurrent.CompletableFuture.failedFuture(
                            new org.apache.kafka.common.errors.TimeoutException("Topic " + t + " not present in metadata"))
                    : real.send(t, inv.getArgument(1), inv.getArgument(2));
        });
        PollingOutboxRelay flakyRelay = new PollingOutboxRelay(flaky, tx, properties.getOutbox());
        beanFactory.autowireBean(flakyRelay);

        tx.executeWithoutResult(s -> {
            publisher.publish(stuckTopic, "user", "stuck-1", new UserCreatedEvent("stuck-1"));
            publisher.publish(topic, "user", "flowing", new UserCreatedEvent("flowing"));
            publisher.publish(stuckTopic, "user", "stuck-2", new UserCreatedEvent("stuck-2"));
        });

        assertThat(flakyRelay.relayBatch()).isEqualTo(1);

        assertThat(poll(consumer, 1)).extracting(ConsumerRecord::key).containsExactly("flowing");
        List<String> waiting = tx.execute(s -> entityManager
                .createQuery("SELECT e.aggregateId FROM OutboxEvent e WHERE e.sentAt IS NULL AND e.failedAt IS NULL ORDER BY e.id", String.class)
                .getResultList());
        assertThat(waiting).containsExactly("stuck-1", "stuck-2");
        // Only the first stuck row was attempted; the second waited behind it to keep the topic's order.
        org.mockito.Mockito.verify(flaky, org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.eq(stuckTopic),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void onlyNonRetriableKafkaErrors_arePermanent() {
        assertThat(PollingOutboxRelay.isPermanent(new org.apache.kafka.common.errors.TimeoutException("t"))).isFalse();
        assertThat(PollingOutboxRelay.isPermanent(new java.util.concurrent.TimeoutException("t"))).isFalse();
        assertThat(PollingOutboxRelay.isPermanent(new org.apache.kafka.common.errors.InvalidTopicException("x"))).isTrue();
        assertThat(PollingOutboxRelay.isPermanent(new org.apache.kafka.common.errors.RecordTooLargeException("x"))).isTrue();
    }

    @Test
    void cleanup_deletesOnlySentRowsPastRetention() {
        tx.executeWithoutResult(s -> {
            publisher.publish(topic, "user", "sent", new UserCreatedEvent("sent"));
        });
        relay.relayBatch();
        tx.executeWithoutResult(s -> publisher.publish(topic, "user", "unsent", new UserCreatedEvent("unsent")));

        assertThat(relay.deleteSentBefore(Instant.now().minusSeconds(60))).isZero();
        assertThat(relay.deleteSentBefore(Instant.now().plusSeconds(60))).isEqualTo(1);
        assertThat(unsentCount()).isEqualTo(1);
    }

    // --- inbox -----------------------------------------------------------------------------------------------

    @Test
    void handler_runsActionOncePerEvent_perHandler() {
        String message = codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserCreatedEvent", new UserCreatedEvent("u1")));
        AtomicInteger calls = new AtomicInteger();

        assertThat(handler.handle("test.a", message, UserCreatedEvent.class, e -> calls.incrementAndGet())).isTrue();
        assertThat(handler.handle("test.a", message, UserCreatedEvent.class, e -> calls.incrementAndGet())).isFalse();
        assertThat(handler.handle("test.b", message, UserCreatedEvent.class, e -> calls.incrementAndGet())).isTrue();

        assertThat(calls).hasValue(2);
    }

    @Test
    void handler_failedAction_isNotMarkedProcessed_soRetryRunsIt() {
        String message = codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserCreatedEvent", new UserCreatedEvent("u1")));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> handler.handle("test.a", message, UserCreatedEvent.class, e -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(handler.handle("test.a", message, UserCreatedEvent.class, e -> calls.incrementAndGet())).isTrue();
        assertThat(calls).hasValue(2);
    }

    @Test
    void handler_exposesSagaToAction_andEventsItPublishJoinTheSaga() {
        UUID sagaId = UUID.randomUUID();
        String message = codec.encode(EventEnvelope.of(sagaId, "UserCreatedEvent", new UserCreatedEvent("u1")));

        handler.handle("test.a", message, UserCreatedEvent.class, e -> {
            assertThat(SagaContext.currentSagaId()).contains(sagaId);
            publisher.publish(topic, "user", "u1", new UserCreatedEvent("u1-followup"));
        });
        assertThat(SagaContext.currentSagaId()).isEmpty();

        relay.relayBatch();
        EventEnvelope<UserCreatedEvent> followUp = codec.decode(poll(consumer, 1).get(0).value(), UserCreatedEvent.class);
        assertThat(followUp.sagaId()).isEqualTo(sagaId);
    }

    @Test
    void handler_rejectsMalformedMessages() {
        assertThatThrownBy(() -> handler.handle("test.a", "{not json", UserCreatedEvent.class, e -> { }))
                .isInstanceOf(EventDecodingException.class);
        assertThatThrownBy(() -> handler.handle("test.a", "{\"type\":\"x\"}", UserCreatedEvent.class, e -> { }))
                .isInstanceOf(EventDecodingException.class);
    }

    // --- error handling ------------------------------------------------------------------------------------

    @Test
    void failingListener_sendsRecordToDlt() {
        try (KafkaConsumer<String, String> dlt = consumer(FailingListener.TOPIC + Topics.DLT_SUFFIX)) {
            kafkaTemplate.send(FailingListener.TOPIC, "k", "poison");

            List<ConsumerRecord<String, String>> records = poll(dlt, 1);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).value()).isEqualTo("poison");
        }
    }

    // --- helpers --------------------------------------------------------------------------------------------

    private long unsentCount() {
        return tx.execute(s -> entityManager
                .createQuery("SELECT COUNT(e) FROM OutboxEvent e WHERE e.sentAt IS NULL", Long.class)
                .getSingleResult());
    }

    private static KafkaConsumer<String, String> consumer(String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Topics are created by the first send; refresh metadata quickly so the subscription picks them up.
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 500);
        KafkaConsumer<String, String> c = new KafkaConsumer<>(props);
        c.subscribe(List.of(topic));
        return c;
    }

    private static List<ConsumerRecord<String, String>> poll(KafkaConsumer<String, String> consumer, int expected) {
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 20_000;
        while (out.size() < expected && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach(out::add);
        }
        // Linger briefly to catch unexpected extra messages.
        consumer.poll(Duration.ofMillis(1000)).forEach(out::add);
        return out;
    }

    /** Jackson fails on this: the getter throws. */
    static class Unserializable {
        public String getValue() {
            throw new IllegalStateException("cannot serialize");
        }
    }
}
