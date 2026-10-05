package com.cadence.auth_service.integration;

import com.cadence.auth_service.consumers.MediaUpdatedConsumer;
import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.events.EventEnvelope;
import com.cadence.events.MediaUpdatedEvent;
import com.cadence.events.Topics;
import com.cadence.events.UserUpdatedEvent;
import com.cadence.messaging.EventCodec;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUpdatedConsumerIT extends BaseIntegrationTest {

    @Autowired MediaUpdatedConsumer consumer;
    @Autowired UserRepository userRepository;
    @Autowired EventCodec codec;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    private String userId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userId = userRepository.save(User.builder()
                .name("Alice").email("alice-media@example.com").role(Role.USER).status(UserStatus.ACTIVE).build()).getId();
    }

    @Test
    void ownAvatar_isApplied_andSnapshotRepublished() {
        consumer.handleMediaUpdated(avatar(userId, "https://cdn/a", userId, false));

        assertThat(userRepository.findById(userId).orElseThrow().getProfileUrl()).isEqualTo("https://cdn/a");
        assertThat(publishedSnapshots()).extracting(UserUpdatedEvent::profileUrl).contains("https://cdn/a");
    }

    @Test
    void someoneElsesAvatar_isIgnored() {
        consumer.handleMediaUpdated(avatar(userId, "https://cdn/evil", "attacker", false));

        assertThat(userRepository.findById(userId).orElseThrow().getProfileUrl()).isNull();
        assertThat(publishedSnapshots()).noneMatch(s -> "https://cdn/evil".equals(s.profileUrl()));
    }

    @Test
    void avatarOfAccountBeingDeleted_isIgnored_soReplicaIsNotRecreated() {
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(UserStatus.DELETING);
        userRepository.save(user);

        consumer.handleMediaUpdated(avatar(userId, "https://cdn/late", userId, false));

        assertThat(userRepository.findById(userId).orElseThrow().getProfileUrl()).isNull();
        assertThat(publishedSnapshots()).isEmpty();
    }

    @Test
    void playlistCoverEvents_areNotForThisService() {
        consumer.handleMediaUpdated(codec.encode(EventEnvelope.of(UUID.randomUUID(), "MediaUpdatedEvent",
                new MediaUpdatedEvent(MediaUpdatedEvent.Target.PLAYLIST_COVER, userId, "https://cdn/p", userId, false))));

        assertThat(userRepository.findById(userId).orElseThrow().getProfileUrl()).isNull();
    }

    private String avatar(String targetId, String url, String requestedBy, boolean admin) {
        return codec.encode(EventEnvelope.of(UUID.randomUUID(), "MediaUpdatedEvent",
                new MediaUpdatedEvent(MediaUpdatedEvent.Target.USER_AVATAR, targetId, url, requestedBy, admin)));
    }

    /** UserUpdatedEvents for this user recorded in the outbox (the relay may already have sent them). */
    private List<UserUpdatedEvent> publishedSnapshots() {
        List<String> payloads = new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT e.payload FROM OutboxEvent e WHERE e.topic = :topic AND e.aggregateId = :id", String.class)
                .setParameter("topic", Topics.USER_UPDATED_TOPIC)
                .setParameter("id", userId)
                .getResultList());
        return payloads.stream().map(p -> codec.decode(p, UserUpdatedEvent.class).payload()).toList();
    }
}
