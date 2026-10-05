package com.cadence.auth_service.saga;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically fails registrations whose saga never replied.
 */
@Component
@RequiredArgsConstructor
public class RegistrationTimeoutSweeper {
    private final RegistrationSaga registrationSaga;

    @Scheduled(fixedDelayString = "${cadence.registration.sweep-interval-ms:60000}",
            initialDelayString = "${cadence.registration.sweep-interval-ms:60000}")
    public void sweep() {
        registrationSaga.failStalePending();
    }
}
