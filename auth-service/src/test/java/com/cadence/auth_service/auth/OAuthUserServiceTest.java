package com.cadence.auth_service.auth;

import com.cadence.auth_service.model.OAuth2Provider;
import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.saga.RegistrationSaga;
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
    @Mock RegistrationSaga registrationSaga;

    @InjectMocks OAuthUserService oAuthUserService;

    @Test
    void findOrCreateUser_returnsExistingUser_withoutStartingSaga() {
        User existing = User.builder().id("u-1").email("bob@example.com").name("Bob").status(UserStatus.ACTIVE).build();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(existing));

        User result = oAuthUserService.findOrCreateUser("bob@example.com", "Bob", "https://bob.pic");

        assertThat(result).isSameAs(existing);
        verify(registrationSaga, never()).start(any());
    }

    @Test
    void findOrCreateUser_startsSaga_forNewGoogleUser() {
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.empty());
        when(registrationSaga.start(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        oAuthUserService.findOrCreateUser("bob@example.com", "Bob", "https://bob.pic");

        ArgumentCaptor<User> started = ArgumentCaptor.forClass(User.class);
        verify(registrationSaga).start(started.capture());
        User saved = started.getValue();
        assertThat(saved.getEmail()).isEqualTo("bob@example.com");
        assertThat(saved.getName()).isEqualTo("Bob");
        assertThat(saved.getProfileUrl()).isEqualTo("https://bob.pic");
        assertThat(saved.getProvider()).isEqualTo(OAuth2Provider.GOOGLE);
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void findOrCreateUser_restartsSaga_forPreviouslyFailedUser() {
        User failed = User.builder().id("u-1").email("bob@example.com").name("Old").status(UserStatus.FAILED).build();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(failed));
        when(registrationSaga.start(failed)).thenReturn(failed);

        User result = oAuthUserService.findOrCreateUser("bob@example.com", "Bob", "https://bob.pic");

        assertThat(result).isSameAs(failed);
        assertThat(failed.getName()).isEqualTo("Bob");
        verify(registrationSaga).start(failed);
    }
}
