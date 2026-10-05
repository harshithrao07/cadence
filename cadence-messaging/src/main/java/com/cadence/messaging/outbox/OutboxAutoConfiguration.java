package com.cadence.messaging.outbox;

import com.cadence.messaging.EventCodec;
import com.cadence.messaging.MessagingAutoConfiguration;
import com.cadence.messaging.MessagingProperties;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import com.cadence.messaging.inbox.ProcessedEvent;
import com.cadence.messaging.kafka.StringKafkaSender;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Database-backed part of cadence-messaging, active in services that use JPA. Registers the {@code outbox} and
 * {@code processed_events} entities with the service's entity scan.
 */
@AutoConfiguration(after = {HibernateJpaAutoConfiguration.class, MessagingAutoConfiguration.class})
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnBean(EntityManagerFactory.class)
@AutoConfigurationPackage(basePackageClasses = {OutboxEvent.class, ProcessedEvent.class})
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(EventCodec codec) {
        return new JpaOutboxPublisher(codec);
    }

    @Bean
    @ConditionalOnMissingBean
    public PollingOutboxRelay pollingOutboxRelay(StringKafkaSender sender,
                                                 PlatformTransactionManager transactionManager,
                                                 MessagingProperties properties) {
        return new PollingOutboxRelay(sender.template(), new TransactionTemplate(transactionManager), properties.getOutbox());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentEventHandler idempotentEventHandler(EventCodec codec) {
        return new IdempotentEventHandler(codec);
    }
}
