package com.cadence.playlist_service.config;

import com.cadence.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics playlist-service produces (registration saga replies).
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic likedSongsCreatedTopic() {
        return TopicBuilder.name(Topics.LIKED_SONGS_CREATED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic likedSongsFailedTopic() {
        return TopicBuilder.name(Topics.LIKED_SONGS_FAILED_TOPIC).partitions(3).replicas(1).build();
    }
}
