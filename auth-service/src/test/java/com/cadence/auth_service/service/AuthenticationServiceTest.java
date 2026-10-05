package com.cadence.auth_service.service;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.auth.AuthenticateRequestDTO;
import com.cadence.auth_service.dto.auth.AuthenticationResponseDTO;
import com.cadence.auth_service.dto.auth.RegisterRequestDTO;
import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.saga.RegistrationSaga;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RegistrationSaga registrationSaga;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User savedUser() {
        return User.builder()
                .id("user-1")
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @org.junit.jupiter.api.BeforeEach
    void setAwaitTimeout() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                authenticationService, "registrationAwaitTimeout", java.time.Duration.ofSeconds(5));
    }

    /** Saga start echoes the user back with an id, as the real one does after saving. */
    private void sagaStartAssignsId() {
        when(registrationSaga.start(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-1");
            u.setStatus(UserStatus.PENDING);
            return u;
        });
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_returns201_andTokens_whenSagaCompletesInTime() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "StrongPass1!");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("hashed");
        sagaStartAssignsId();
        when(registrationSaga.awaitOutcome(eq("user-1"), any())).thenReturn(UserStatus.ACTIVE);
        when(jwtUtil.generateToken(any(User.class), anyLong())).thenReturn("access-token", "refresh-token");

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response = authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().id()).isEqualTo("user-1");
        assertThat(response.getBody().data().accessToken()).isEqualTo("access-token");
        assertThat(response.getBody().data().refreshToken()).isEqualTo("refresh-token");
        org.mockito.ArgumentCaptor<User> started = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(registrationSaga).start(started.capture());
        assertThat(started.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(started.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void register_returns202_withoutTokens_whenSagaStillPending() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "StrongPass1!");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        sagaStartAssignsId();
        when(registrationSaga.awaitOutcome(eq("user-1"), any())).thenReturn(UserStatus.PENDING);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response = authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().id()).isEqualTo("user-1");
        assertThat(response.getBody().data().accessToken()).isNull();
        verify(jwtUtil, never()).generateToken(any(User.class), anyLong());
    }

    @Test
    void register_returns503_whenSagaFails() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "StrongPass1!");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        sagaStartAssignsId();
        when(registrationSaga.awaitOutcome(eq("user-1"), any())).thenReturn(UserStatus.FAILED);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response = authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().success()).isFalse();
        verify(jwtUtil, never()).generateToken(any(User.class), anyLong());
    }

    @Test
    void register_returns409_whenEmailBelongsToActiveOrPendingUser() {
        for (UserStatus status : java.util.List.of(UserStatus.ACTIVE, UserStatus.PENDING)) {
            User existing = savedUser();
            existing.setStatus(status);
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));

            ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                    authenticationService.register(new RegisterRequestDTO("Alice", "alice@example.com", "StrongPass1!"));

            assertThat(response.getStatusCode()).as(status.name()).isEqualTo(HttpStatus.CONFLICT);
        }
        verify(registrationSaga, never()).start(any());
    }

    @Test
    void register_restartsSaga_onSameRow_whenPreviousAttemptFailed() {
        User failed = savedUser();
        failed.setStatus(UserStatus.FAILED);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(failed));
        when(passwordEncoder.encode("NewStrongPass1!")).thenReturn("new-hash");
        when(registrationSaga.start(failed)).thenReturn(failed);
        when(registrationSaga.awaitOutcome(eq("user-1"), any())).thenReturn(UserStatus.PENDING);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.register(new RegisterRequestDTO("Alice B", "alice@example.com", "NewStrongPass1!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(failed.getName()).isEqualTo("Alice B");
        assertThat(failed.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void register_returns400_whenPasswordTooShort() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "short1!");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response = authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(registrationSaga, never()).start(any());
    }

    @Test
    void register_returns400_whenPasswordHasNoSpecialCharOrDigit() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "alllowercaseletters");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response = authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(registrationSaga, never()).start(any());
    }

    @Test
    void register_returns400_whenPasswordIsBlank() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "   ");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response = authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(registrationSaga, never()).start(any());
    }

    @Test
    void authenticate_returns403_whileAccountIsPendingOrFailed() {
        for (UserStatus status : java.util.List.of(UserStatus.PENDING, UserStatus.FAILED)) {
            User user = savedUser();
            user.setStatus(status);
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("ValidPass1!", "hashed")).thenReturn(true);

            ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                    authenticationService.authenticate(new AuthenticateRequestDTO("alice@example.com", "ValidPass1!"));

            assertThat(response.getStatusCode()).as(status.name()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        verify(jwtUtil, never()).generateToken(any(User.class), anyLong());
    }

    // ── authenticate ──────────────────────────────────────────────────────────

    @Test
    void authenticate_returns201_andTokens_onValidCredentials() {
        AuthenticateRequestDTO dto = new AuthenticateRequestDTO("alice@example.com", "ValidPass1!");
        User user = savedUser();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPass1!", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(any(User.class), anyLong()))
                .thenReturn("access-token", "refresh-token");

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.authenticate(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().accessToken()).isEqualTo("access-token");
    }

    @Test
    void authenticate_returns400_whenUserNotFound() {
        AuthenticateRequestDTO dto = new AuthenticateRequestDTO("nobody@example.com", "pass");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.authenticate(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("User does not exist");
    }

    @Test
    void authenticate_returns400_whenPasswordDoesNotMatch() {
        AuthenticateRequestDTO dto = new AuthenticateRequestDTO("alice@example.com", "WrongPass!");
        User user = savedUser();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass!", "hashed")).thenReturn(false);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.authenticate(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Password does not match");
    }

    // ── validateEmail ─────────────────────────────────────────────────────────

    @Test
    void validateEmail_returnsTrue_whenEmailExists() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        ResponseEntity<ApiResponseDTO<Boolean>> response =
                authenticationService.validateEmail("alice@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("User already exists");
    }

    @Test
    void validateEmail_returnsFalse_whenEmailDoesNotExist() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        ResponseEntity<ApiResponseDTO<Boolean>> response =
                authenticationService.validateEmail("new@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("User does not exist");
    }
}
