package com.cadence.auth_service.integration;

import com.cadence.auth_service.dto.auth.AuthenticateRequestDTO;
import com.cadence.auth_service.dto.auth.RegisterRequestDTO;
import com.cadence.auth_service.model.UserDeletion;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.repository.UserDeletionRepository;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.saga.UserDeletionSaga;
import com.cadence.auth_service.service.AuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The account deletion saga over real Kafka: auth's outbox + relay, FakeDeletionParticipants' confirmations, auth's
 * confirmation listener.
 */
class UserDeletionSagaIT extends BaseIntegrationTest {

    private static final String EMAIL = "delete-me@example.com";
    private static final String PASSWORD = "StrongPass1!";

    @Autowired AuthenticationService authenticationService;
    @Autowired UserDeletionSaga userDeletionSaga;
    @Autowired UserRepository userRepository;
    @Autowired UserDeletionRepository userDeletionRepository;

    private String userId;

    @BeforeEach
    void setUp() {
        userDeletionRepository.deleteAll();
        userRepository.deleteAll();
        FakePlaylistParticipant.mode = FakePlaylistParticipant.Mode.SUCCEED;
        FakeDeletionParticipants.silent = Set.of();
        userId = authenticationService.register(new RegisterRequestDTO("Del", EMAIL, PASSWORD)).getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        FakeDeletionParticipants.silent = Set.of();
        ReflectionTestUtils.setField(userDeletionSaga, "retryAfter", Duration.ofMinutes(10));
    }

    @Test
    void allConfirm_userIsDeleted_andEmailCanRegisterAgain() {
        assertThat(userDeletionSaga.request(userId)).isTrue();

        await(() -> userRepository.findById(userId).isEmpty());
        UserDeletion deletion = userDeletionRepository.findById(userId).orElseThrow();
        assertThat(deletion.getCompletedAt()).isNotNull();
        assertThat(deletion.allPurged()).isTrue();

        assertThat(login().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(authenticationService.register(new RegisterRequestDTO("Del Again", EMAIL, PASSWORD)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void whileDeleting_loginIsRefused_andRequestIsIdempotent() {
        FakeDeletionParticipants.silent = Set.of("playlist", "catalog", "streaming");

        assertThat(userDeletionSaga.request(userId)).isTrue();
        assertThat(userDeletionSaga.request(userId)).isTrue();

        assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.DELETING);
        assertThat(login().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authenticationService.register(new RegisterRequestDTO("Del", EMAIL, PASSWORD)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(userDeletionRepository.count()).isEqualTo(1);
    }

    @Test
    void missingConfirmation_keepsUser_untilRetryGetsIt() {
        FakeDeletionParticipants.silent = Set.of("streaming");
        userDeletionSaga.request(userId);

        await(() -> {
            UserDeletion d = userDeletionRepository.findById(userId).orElseThrow();
            return d.getPlaylistPurgedAt() != null && d.getCatalogPurgedAt() != null;
        });
        assertThat(userDeletionRepository.findById(userId).orElseThrow().getStreamingPurgedAt()).isNull();
        assertThat(userRepository.findById(userId)).as("not deleted before every service confirmed").isPresent();

        // streaming comes back; the sweeper re-sends the request and the saga completes.
        FakeDeletionParticipants.silent = Set.of();
        ReflectionTestUtils.setField(userDeletionSaga, "retryAfter", Duration.ZERO);
        assertThat(userDeletionSaga.retryStale()).isEqualTo(1);

        await(() -> userRepository.findById(userId).isEmpty());
        assertThat(userDeletionSaga.retryStale()).as("completed deletions aren't retried").isZero();
    }

    @Test
    void unknownUser_isReported() {
        assertThat(userDeletionSaga.request("no-such-user")).isFalse();
    }

    private org.springframework.http.ResponseEntity<?> login() {
        return authenticationService.authenticate(new AuthenticateRequestDTO(EMAIL, PASSWORD));
    }

    private static void await(java.util.concurrent.Callable<Boolean> condition) {
        long deadline = System.currentTimeMillis() + 20_000;
        try {
            while (!condition.call()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new AssertionError("condition not met within 20 s");
                }
                Thread.sleep(200);
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
