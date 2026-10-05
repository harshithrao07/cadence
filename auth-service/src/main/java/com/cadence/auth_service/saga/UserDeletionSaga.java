package com.cadence.auth_service.saga;

import com.cadence.auth_service.model.User;
import com.cadence.auth_service.model.UserDeletion;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.producers.UserDeletionRequestedProducer;
import com.cadence.auth_service.repository.EmailVerificationTokenRepository;
import com.cadence.auth_service.repository.UserDeletionRepository;
import com.cadence.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * auth-service's side of the account deletion saga (choreography, forward-only):
 * <pre>
 * request        user DELETING (no more logins or token refreshes) + auth.user-deletion-requested
 * participants   playlist / catalog / streaming purge the user  + &lt;service&gt;.user-data-purged
 * onPurged       record each confirmation; when all three are in, delete the user row
 * retry          re-send the request for deletions still incomplete after a while (covers dead-lettered messages)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletionSaga {
    public static final String PLAYLIST = "playlist";
    public static final String CATALOG = "catalog";
    public static final String STREAMING = "streaming";

    private final UserRepository userRepository;
    private final UserDeletionRepository userDeletionRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserDeletionRequestedProducer userDeletionRequestedProducer;

    @Value("${cadence.user-deletion.retry-after:PT10M}")
    private Duration retryAfter;

    /**
     * Starts deleting the account. Idempotent: asking again while a deletion is running changes nothing.
     *
     * @return false if the user doesn't exist
     */
    @Transactional
    public boolean request(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        if (user.getStatus() == UserStatus.DELETING) {
            return true;
        }
        user.setStatus(UserStatus.DELETING);
        userRepository.save(user);
        userDeletionRepository.save(new UserDeletion(userId, Instant.now()));
        userDeletionRequestedProducer.send(userId);
        log.info("Account deletion saga started for user {}", userId);
        return true;
    }

    /** A participant confirmed it purged the user. Deletes the user once every participant has. */
    @Transactional
    public void onPurged(String userId, String service) {
        UserDeletion deletion = userDeletionRepository.findById(userId).orElse(null);
        if (deletion == null || deletion.getCompletedAt() != null) {
            return;
        }
        Instant now = Instant.now();
        switch (service) {
            case PLAYLIST -> deletion.setPlaylistPurgedAt(now);
            case CATALOG -> deletion.setCatalogPurgedAt(now);
            case STREAMING -> deletion.setStreamingPurgedAt(now);
            default -> {
                log.warn("Ignoring purge confirmation from unknown service {} for user {}", service, userId);
                return;
            }
        }
        if (deletion.allPurged()) {
            userRepository.findById(userId).ifPresent(user -> {
                emailVerificationTokenRepository.deleteByUser(user);
                userRepository.delete(user);
            });
            deletion.setCompletedAt(now);
            log.info("Account deletion saga completed: user {} deleted", userId);
        }
        userDeletionRepository.save(deletion);
    }

    /** Re-sends the request for deletions that haven't completed within the retry interval. */
    @Transactional
    public int retryStale() {
        Instant now = Instant.now();
        List<UserDeletion> stale = userDeletionRepository.findAllByCompletedAtIsNullAndLastRequestedAtBefore(now.minus(retryAfter));
        stale.forEach(deletion -> {
            deletion.setLastRequestedAt(now);
            userDeletionRequestedProducer.send(deletion.getUserId());
            log.warn("Re-sending account deletion request for user {} (requested {})", deletion.getUserId(), deletion.getRequestedAt());
        });
        userDeletionRepository.saveAll(stale);
        return stale.size();
    }
}
