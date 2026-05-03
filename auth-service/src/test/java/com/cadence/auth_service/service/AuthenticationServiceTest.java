package com.cadence.auth_service.service;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.auth.AuthenticateRequestDTO;
import com.cadence.auth_service.dto.auth.AuthenticationResponseDTO;
import com.cadence.auth_service.dto.auth.RegisterRequestDTO;
import com.cadence.auth_service.events.UserCreatedEvent;
import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.producers.UserCreatedProducer;
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

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserCreatedProducer producer;
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
                .build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_returns201_andTokens_onSuccess() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "ValidPass1!");
        User saved = savedUser();
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtUtil.generateToken(any(User.class), anyLong()))
                .thenReturn("access-token", "refresh-token");

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().accessToken()).isEqualTo("access-token");
        assertThat(response.getBody().data().refreshToken()).isEqualTo("refresh-token");
        assertThat(response.getBody().data().id()).isEqualTo("user-1");
        verify(producer).send(any(UserCreatedEvent.class));
    }

    @Test
    void register_returns409_whenEmailAlreadyExists() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "ValidPass1!");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().success()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_returns400_whenPasswordTooShort() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "Short1!");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("10 characters");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_returns400_whenPasswordHasNoSpecialCharOrDigit() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "OnlyLettersHere");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_returns400_whenPasswordIsBlank() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Alice", "alice@example.com", "          ");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> response =
                authenticationService.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
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
