package com.cadence.auth_service.auth;

import com.cadence.auth_service.model.OAuth2Provider;
import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.saga.RegistrationSaga;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthUserService {
    private final UserRepository userRepository;
    private final RegistrationSaga registrationSaga;

    /**
     * Returns the user for this Google account, starting the registration saga for a new one (or restarting it
     * for a FAILED one). The returned user may still be PENDING.
     */
    @Transactional
    public User findOrCreateUser(String email, String name, String picture) {
        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null && existing.getStatus() != UserStatus.FAILED) {
            return existing;
        }

        User user = existing != null ? existing : new User();
        user.setEmail(email);
        user.setName(name);
        user.setProfileUrl(picture);
        user.setProvider(OAuth2Provider.GOOGLE);
        if (existing == null) {
            user.setRole(Role.USER);
        }
        return registrationSaga.start(user);
    }
}
