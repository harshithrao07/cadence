package com.cadence.messaging.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Producer for envelope JSON strings, used by the outbox relay and the dead-letter recoverer. Deliberately not a
 * KafkaTemplate / ProducerFactory bean, so Spring Boot's own auto-configured producer is left untouched.
 * <p>
 * Config is copied from Boot's producer factory rather than {@code KafkaProperties}: only the factory includes
 * connection details such as a Testcontainers {@code @ServiceConnection}.
 */
public class StringKafkaSender implements DisposableBean {
    private final DefaultKafkaProducerFactory<String, String> producerFactory;
    private final KafkaTemplate<String, String> template;

    /**
     * @param maxBlock cap on how long {@code send()} itself may block (waiting for topic metadata). Kafka's default
     *                 is 60 s, which would stall the relay for a minute per attempt on a missing topic.
     */
    public StringKafkaSender(ProducerFactory<?, ?> bootProducerFactory, Duration maxBlock) {
        Map<String, Object> config = new HashMap<>(bootProducerFactory.getConfigurationProperties());
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlock.toMillis());
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        this.producerFactory = new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new StringSerializer());
        this.template = new KafkaTemplate<>(producerFactory);
    }

    public KafkaTemplate<String, String> template() {
        return template;
    }

    @Override
    public void destroy() {
        producerFactory.destroy();
    }
}
