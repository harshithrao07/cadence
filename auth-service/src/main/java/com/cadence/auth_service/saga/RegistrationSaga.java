package com.cadence.auth_service.saga;

import com.cadence.auth_service.model.User;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.producers.UserRegisteredProducer;
import com.cadence.auth_service.producers.UserUpdatedProducer;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.events.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * auth-service's side of the registration saga (choreography):
 * <pre>
 * start          user PENDING            + auth.user-registered
 * playlist       Liked Songs created     + playlist.liked-songs-created | playlist.liked-songs-failed
 * onCreated      user ACTIVE             + auth.user-updated (catalog's user_replica)
 * onFailed       user FAILED             (compensation; the user may register again)
 * sweeper        PENDING too long -> FAILED (covers replies that never come, e.g. dead-lettered requests)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationSaga {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final UserRepository userRepository;
    private final UserRegisteredProducer userRegisteredProducer;
    private final UserUpdatedProducer userUpdatedProducer;

    @Value("${cadence.registration.pending-timeout:PT5M}")
    private Duration pendingTimeout;

    /**
     * Starts (or, for a FAILED user, restarts) the saga for the given user, which must already carry its
     * profile fields. Call inside the transaction that saves the user.
     */
    @Transactional
    public User start(User user) {
        user.setStatus(UserStatus.PENDING);
        user.setRegisteredAt(Instant.now());
        User saved = userRepository.save(user);
        userRegisteredProducer.send(new UserRegisteredEvent(saved.getId()));
        log.info("Registration saga started for user {}", saved.getId());
        return saved;
    }

    /** Reply: provisioning succeeded. A late success after a timeout still activates the user. */
    @Transactional
    public void onLikedSongsCreated(String userId) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            log.warn("Liked Songs created for unknown user {}", userId);
            return;
        }
        User user = found.get();
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        userUpdatedProducer.send(user);
        log.info("Registration saga completed: user {} ACTIVE", userId);
    }

    /** Reply: provisioning failed. Compensate by failing the registration. */
    @Transactional
    public void onLikedSongsFailed(String userId, String reason) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getStatus() == UserStatus.PENDING) {
                user.setStatus(UserStatus.FAILED);
                userRepository.save(user);
                log.warn("Registration saga failed for user {}: {}", userId, reason);
            }
        });
    }

    /** Fails registrations that have been PENDING longer than the timeout. */
    @Transactional
    public int failStalePending() {
        List<User> stale = userRepository.findAllByStatusAndRegisteredAtBefore(
                UserStatus.PENDING, Instant.now().minus(pendingTimeout));
        stale.forEach(user -> {
            user.setStatus(UserStatus.FAILED);
            log.warn("Registration saga timed out for user {} (pending since {})", user.getId(), user.getRegisteredAt());
        });
        userRepository.saveAll(stale);
        return stale.size();
    }

    /**
     * Waits (outside any transaction) until the user leaves PENDING or the timeout passes. Reads the status with a
     * query each time, so it also works in a web request where open-in-view holds the PENDING User in the cache.
     *
     * @return the user's status at that point; PENDING means "still in progress"
     */
    public UserStatus awaitOutcome(String userId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            UserStatus status = userRepository.findStatusById(userId).orElse(UserStatus.FAILED);
            if (status != UserStatus.PENDING || System.nanoTime() >= deadline) {
                return status;
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return status;
            }
        }
    }
}
