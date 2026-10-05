package com.cadence.streaming_service.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.SongsDeletedEvent;
import com.cadence.messaging.EventCodec;
import com.cadence.streaming_service.consumers.SongsDeletedConsumer;
import com.cadence.streaming_service.repository.PlayHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SongsDeletedConsumerIT extends BaseIntegrationTest {

    @Autowired SongsDeletedConsumer consumer;
    @Autowired PlayHistoryRepository playHistoryRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EventCodec codec;

    @BeforeEach
    void setUp() {
        playHistoryRepository.deleteAll();
        for (String[] row : new String[][]{{"u1", "gone"}, {"u2", "gone"}, {"u1", "kept"}}) {
            jdbcTemplate.update(
                    "INSERT INTO play_history (user_id, song_id, play_count, created_at, last_played_at) VALUES (?, ?, 3, NOW(6), NOW(6))",
                    row[0], row[1]);
        }
    }

    @Test
    void historyOfDeletedSongs_isRemoved_forEveryUser_andRedeliveryIsHarmless() {
        String message = codec.encode(EventEnvelope.of(UUID.randomUUID(), "SongsDeletedEvent",
                new SongsDeletedEvent("record-1", List.of("gone"))));

        consumer.handleSongsDeleted(message);
        consumer.handleSongsDeleted(message);

        assertThat(jdbcTemplate.queryForList("SELECT song_id FROM play_history", String.class)).containsExactly("kept");
    }
}
