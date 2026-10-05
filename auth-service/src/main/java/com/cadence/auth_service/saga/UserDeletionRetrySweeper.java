package com.cadence.auth_service.saga;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically re-sends account deletion requests that some participant hasn't confirmed yet.
 */
@Component
@RequiredArgsConstructor
public class UserDeletionRetrySweeper {
    private final UserDeletionSaga userDeletionSaga;

    @Scheduled(fixedDelayString = "${cadence.user-deletion.sweep-interval-ms:300000}",
            initialDelayString = "${cadence.user-deletion.sweep-interval-ms:300000}")
    public void sweep() {
        userDeletionSaga.retryStale();
    }
}
