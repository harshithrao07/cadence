package com.cadence.auth_service.config;

import com.cadence.auth_service.dto.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name(Topics.USER_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailVerificationTopic() {
        return TopicBuilder.name(Topics.EMAIL_VERIFICATION_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
