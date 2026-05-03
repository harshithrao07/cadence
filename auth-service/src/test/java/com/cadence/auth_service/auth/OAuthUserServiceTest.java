package com.cadence.auth_service.auth;

import com.cadence.auth_service.events.UserCreatedEvent;
import com.cadence.auth_service.model.OAuth2Provider;
import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.producers.UserCreatedProducer;
import com.cadence.auth_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserCreatedProducer producer;

    @InjectMocks OAuthUserService oAuthUserService;

    @Test
    void findOrCreateUser_returnsExistingUser_whenEmailFound() {
        User existing = User.builder()
                .id("user-1")
                .email("alice@example.com")
                .name("Alice")
                .provider(OAuth2Provider.GOOGLE)
                .build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));

        User result = oAuthUserService.findOrCreateUser("alice@example.com", "Alice", "https://pic");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
        verify(producer, never()).send(any());
    }

    @Test
    void findOrCreateUser_createsNewUser_andPublishesEvent_whenEmailNotFound() {
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-new");
            return u;
        });

        User result = oAuthUserService.findOrCreateUser("bob@example.com", "Bob", "https://bob.pic");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("bob@example.com");
        assertThat(saved.getName()).isEqualTo("Bob");
        assertThat(saved.getProfileUrl()).isEqualTo("https://bob.pic");
        assertThat(saved.getProvider()).isEqualTo(OAuth2Provider.GOOGLE);
        assertThat(saved.getRole()).isEqualTo(Role.USER);

        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(producer).send(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo("user-new");

        assertThat(result.getId()).isEqualTo("user-new");
    }
}
