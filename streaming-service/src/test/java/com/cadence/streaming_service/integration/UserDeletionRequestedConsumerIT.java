package com.cadence.streaming_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.Topics;
import com.cadence.events.UserDataPurgedEvent;
import com.cadence.events.UserDeletionRequestedEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.streaming_service.consumers.UserDeletionRequestedConsumer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDeletionRequestedConsumerIT extends BaseIntegrationTest {

    @Autowired UserDeletionRequestedConsumer consumer;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EventCodec codec;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        for (String table : List.of("play_history", "outbox", "processed_events")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        for (String[] row : new String[][]{{"leaving", "s1"}, {"leaving", "s2"}, {"staying", "s1"}}) {
            jdbcTemplate.update(
                    "INSERT INTO play_history (user_id, song_id, play_count, created_at, last_played_at) VALUES (?, ?, 1, NOW(6), NOW(6))",
                    row[0], row[1]);
        }
    }

    @Test
    void purgesThatUsersHistory_andConfirmsOnce() {
        String message = codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserDeletionRequestedEvent", new UserDeletionRequestedEvent("leaving")));

        consumer.handleUserDeletionRequested(message);
        consumer.handleUserDeletionRequested(message);

        assertThat(jdbcTemplate.queryForList("SELECT user_id FROM play_history", String.class)).containsExactly("staying");
        List<String> replies = new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT e.payload FROM OutboxEvent e WHERE e.topic = :t", String.class)
                .setParameter("t", Topics.STREAMING_USER_DATA_PURGED_TOPIC).getResultList());
        assertThat(replies).singleElement().satisfies(p ->
                assertThat(codec.decode(p, UserDataPurgedEvent.class).payload()).isEqualTo(new UserDataPurgedEvent("leaving", "streaming")));
    }
}
