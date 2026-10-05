package com.project.cadence.integration;

import com.cadence.events.EventEnvelope;
import com.cadence.events.Topics;
import com.cadence.events.UserDataPurgedEvent;
import com.cadence.events.UserDeletionRequestedEvent;
import com.cadence.messaging.EventCodec;
import com.project.cadence.consumers.UserDeletionRequestedConsumer;
import com.project.cadence.model.Artist;
import com.project.cadence.repository.ArtistRepository;
import com.project.cadence.repository.UserReplicaRepository;
import com.project.cadence.service.AwsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDeletionRequestedConsumerIT extends BaseIntegrationTest {

    @Autowired UserDeletionRequestedConsumer consumer;
    @Autowired UserReplicaRepository userReplicaRepository;
    @Autowired ArtistRepository artistRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EventCodec codec;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    @MockBean AwsService awsService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("artist_following", "user_replica", "outbox", "processed_events",
                "artist_created_songs", "artist_records", "song_genre", "song", "record", "artist")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        String artist = artistRepository.save(Artist.builder().name("Drake").build()).getId();
        for (String user : List.of("leaving", "staying")) {
            jdbcTemplate.update("INSERT INTO user_replica (id, name, source_updated_at) VALUES (?, ?, NOW(6))", user, user);
            jdbcTemplate.update("INSERT INTO artist_following (user_id, artist_id, follow_order) VALUES (?, ?, 0)", user, artist);
        }
    }

    @Test
    void purgesFollowsAndReplica_ofThatUserOnly_andConfirmsOnce() {
        String message = codec.encode(EventEnvelope.of(UUID.randomUUID(), "UserDeletionRequestedEvent", new UserDeletionRequestedEvent("leaving")));

        consumer.handleUserDeletionRequested(message);
        consumer.handleUserDeletionRequested(message);

        assertThat(jdbcTemplate.queryForList("SELECT user_id FROM artist_following", String.class)).containsExactly("staying");
        assertThat(userReplicaRepository.findById("leaving")).isEmpty();
        assertThat(userReplicaRepository.findById("staying")).isPresent();
        List<String> replies = new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT e.payload FROM OutboxEvent e WHERE e.topic = :t", String.class)
                .setParameter("t", Topics.CATALOG_USER_DATA_PURGED_TOPIC).getResultList());
        assertThat(replies).singleElement().satisfies(p ->
                assertThat(codec.decode(p, UserDataPurgedEvent.class).payload()).isEqualTo(new UserDataPurgedEvent("leaving", "catalog")));
    }
}
