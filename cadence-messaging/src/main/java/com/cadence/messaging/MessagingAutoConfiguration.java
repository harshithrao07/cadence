package com.cadence.messaging;

import com.cadence.events.Topics;
import com.cadence.messaging.kafka.StringKafkaSender;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Always-on part of cadence-messaging: event codec, a String producer, and the Kafka error handler
 * (exponential retries, then the record is published to {@code <topic>.DLT}).
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MessagingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EventCodec eventCodec() {
        return new EventCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public StringKafkaSender stringKafkaSender(ProducerFactory<?, ?> producerFactory, MessagingProperties properties) {
        return new StringKafkaSender(producerFactory, properties.getOutbox().getSendTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler cadenceKafkaErrorHandler(StringKafkaSender sender, MessagingProperties properties) {
        // Negative partition: let Kafka pick, so the DLT doesn't need as many partitions as the source topic.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                sender.template(),
                (record, ex) -> {
                    log.error("Sending record from {}-{}@{} to DLT after failure: {}",
                            record.topic(), record.partition(), record.offset(), ex.getMessage());
                    return new TopicPartition(record.topic() + Topics.DLT_SUFFIX, -1);
                });

        MessagingProperties.Consumer consumer = properties.getConsumer();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(consumer.getMaxRetries());
        backOff.setInitialInterval(consumer.getInitialBackoff().toMillis());
        backOff.setMultiplier(consumer.getBackoffMultiplier());
        backOff.setMaxInterval(consumer.getMaxBackoff().toMillis());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(EventDecodingException.class);
        return handler;
    }
}
