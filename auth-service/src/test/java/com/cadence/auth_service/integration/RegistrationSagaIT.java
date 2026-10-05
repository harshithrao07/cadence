package com.cadence.auth_service.integration;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.auth.AuthenticateRequestDTO;
import com.cadence.auth_service.dto.auth.AuthenticationResponseDTO;
import com.cadence.auth_service.dto.auth.RegisterRequestDTO;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.saga.RegistrationSaga;
import com.cadence.auth_service.service.AuthenticationService;
import com.cadence.events.Topics;
import com.cadence.messaging.outbox.OutboxEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registration saga end to end over real Kafka: auth's outbox + relay, FakePlaylistParticipant's replies,
 * auth's reply listeners.
 */
class RegistrationSagaIT extends BaseIntegrationTest {

    private static final String EMAIL = "saga@example.com";
    private static final String PASSWORD = "StrongPass1!";

    @Autowired AuthenticationService authenticationService;
    @Autowired RegistrationSaga registrationSaga;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        FakePlaylistParticipant.mode = FakePlaylistParticipant.Mode.SUCCEED;
    }

    @AfterEach
    void tearDown() {
        FakePlaylistParticipant.mode = FakePlaylistParticipant.Mode.SUCCEED;
        ReflectionTestUtils.setField(registrationSaga, "pendingTimeout", Duration.ofMinutes(5));
    }

    @Test
    void happyPath_activatesUser_returnsTokens_andPublishesSnapshotInSameSaga() {
        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response = register("Alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().accessToken()).isNotBlank();
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        OutboxEvent registered = outbox(Topics.USER_REGISTERED_TOPIC, user.getId()).get(0);
        OutboxEvent snapshot = outbox(Topics.USER_UPDATED_TOPIC, user.getId()).get(0);
        assertThat(snapshot.getSagaId()).as("activation joins the registration saga").isEqualTo(registered.getSagaId());

        assertThat(login().getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Regression: in a real HTTP request, open-in-view binds one EntityManager to the thread for the whole request,
     * so the wait loop must not read the user through it (it kept seeing the cached PENDING user and answered 202).
     */
    @Test
    void happyPath_insideWebRequestWithOpenEntityManager_stillSeesActivation() {
        EntityManager requestEm = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(requestEm));
        try {
            assertThat(register("Alice").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            requestEm.close();
        }
    }

    @Test
    void failureReply_compensates_userFailed_noSnapshot_canRegisterAgain() {
        FakePlaylistParticipant.mode = FakePlaylistParticipant.Mode.FAIL;

        assertThat(register("Alice").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        User failed = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(UserStatus.FAILED);
        assertThat(outbox(Topics.USER_UPDATED_TOPIC, failed.getId())).isEmpty();
        assertThat(login().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        FakePlaylistParticipant.mode = FakePlaylistParticipant.Mode.SUCCEED;
        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> retry = register("Alice Again");

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        User active = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(active.getId()).as("same row reused").isEqualTo(failed.getId());
        assertThat(active.getName()).isEqualTo("Alice Again");
        assertThat(active.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void noReply_answers202_blocksLogin_thenSweeperFails_andLateReplyStillActivates() {
        FakePlaylistParticipant.mode = FakePlaylistParticipant.Mode.IGNORE;

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response = register("Alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().data().accessToken()).isNull();
        String userId = response.getBody().data().id();
        assertThat(login().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ReflectionTestUtils.setField(registrationSaga, "pendingTimeout", Duration.ZERO);
        assertThat(registrationSaga.failStalePending()).isEqualTo(1);
        assertThat(status(userId)).isEqualTo(UserStatus.FAILED);

        // The playlist eventually got provisioned after all: the user is usable.
        registrationSaga.onLikedSongsCreated(userId);
        assertThat(status(userId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(outbox(Topics.USER_UPDATED_TOPIC, userId)).hasSize(1);
    }

    @Test
    void duplicateSuccessReply_isHarmless() {
        register("Alice");
        String userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        registrationSaga.onLikedSongsCreated(userId);

        assertThat(status(userId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(outbox(Topics.USER_UPDATED_TOPIC, userId)).as("no second snapshot").hasSize(1);
    }

    @Test
    void sweeper_ignoresRecentPendingRegistrations() {
        FakePlaylistParticipant.mode = FakePlaylistParticipant.Mode.IGNORE;
        String userId = register("Alice").getBody().data().id();

        assertThat(registrationSaga.failStalePending()).isZero();
        assertThat(status(userId)).isEqualTo(UserStatus.PENDING);
    }

    private ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> register(String name) {
        return authenticationService.register(new RegisterRequestDTO(name, EMAIL, PASSWORD));
    }

    private ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> login() {
        return authenticationService.authenticate(new AuthenticateRequestDTO(EMAIL, PASSWORD));
    }

    private UserStatus status(String userId) {
        return userRepository.findById(userId).orElseThrow().getStatus();
    }

    private List<OutboxEvent> outbox(String topic, String aggregateId) {
        return new TransactionTemplate(transactionManager).execute(s -> entityManager
                .createQuery("SELECT e FROM OutboxEvent e WHERE e.topic = :topic AND e.aggregateId = :id ORDER BY e.id", OutboxEvent.class)
                .setParameter("topic", topic)
                .setParameter("id", aggregateId)
                .getResultList());
    }
}
