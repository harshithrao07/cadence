package com.cadence.auth_service.auth;

import com.cadence.events.UserCreatedEvent;
import com.cadence.auth_service.model.OAuth2Provider;
import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.producers.UserCreatedProducer;
import com.cadence.auth_service.producers.UserUpdatedProducer;
import com.cadence.auth_service.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthUserService {
    private final UserRepository userRepository;
    private final UserCreatedProducer producer;
    private final UserUpdatedProducer userUpdatedProducer;

    @Transactional
    public User findOrCreateUser(String email, String name, String picture) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setProfileUrl(picture);
                    newUser.setProvider(OAuth2Provider.GOOGLE);
                    newUser.setRole(Role.USER);

                    User saved = userRepository.save(newUser);

                    producer.send(
                            new UserCreatedEvent(saved.getId())
                    );
                    userUpdatedProducer.send(saved);

                    return saved;
                });
    }
}
