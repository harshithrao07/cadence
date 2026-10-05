package com.cadence.streaming_service.config;

import com.cadence.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics streaming-service produces (account deletion saga confirmations).
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic userDataPurgedTopic() {
        return TopicBuilder.name(Topics.STREAMING_USER_DATA_PURGED_TOPIC).partitions(3).replicas(1).build();
    }
}
