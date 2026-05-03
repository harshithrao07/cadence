package com.cadence.auth_service.service;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.events.EmailVerificationEvent;
import com.cadence.auth_service.model.EmailVerificationToken;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.producers.EmailVerificationProducer;
import com.cadence.auth_service.repository.EmailVerificationTokenRepository;
import com.cadence.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationProducer emailVerificationProducer;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailVerificationService, "backendUrl", "http://localhost:8080/");
    }

    private User user() {
        return User.builder()
                .id("user-1")
                .name("Alice")
                .email("alice@example.com")
                .emailVerified(false)
                .build();
    }

    private EmailVerificationToken token(User user, LocalDateTime expiry) {
        return EmailVerificationToken.builder()
                .id("token-id")
                .token("uuid-token")
                .user(user)
                .expiryDate(expiry)
                .build();
    }

    // ── verifyEmail ───────────────────────────────────────────────────────────

    @Test
    void verifyEmail_marksUserVerified_andDeletesToken_forValidToken() {
        User u = user();
        EmailVerificationToken t = token(u, LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByToken("uuid-token")).thenReturn(Optional.of(t));

        ResponseEntity<?> response = emailVerificationService.verifyEmail("uuid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Email verified successfully");
        assertThat(u.isEmailVerified()).isTrue();
        verify(userRepository).save(u);
        verify(tokenRepository).delete(t);
    }

    @Test
    void verifyEmail_returns400_whenTokenExpired() {
        User u = user();
        EmailVerificationToken t = token(u, LocalDateTime.now().minusHours(1));
        when(tokenRepository.findByToken("uuid-token")).thenReturn(Optional.of(t));

        ResponseEntity<?> response = emailVerificationService.verifyEmail("uuid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Token expired");
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_returns200_whenEmailAlreadyVerified() {
        User u = user();
        u.setEmailVerified(true);
        EmailVerificationToken t = token(u, LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByToken("uuid-token")).thenReturn(Optional.of(t));

        ResponseEntity<?> response = emailVerificationService.verifyEmail("uuid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Email already verified");
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_throwsException_whenTokenNotFound() {
        when(tokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verifyEmail("bad-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid token");
    }

    // ── generateToken ─────────────────────────────────────────────────────────

    @Test
    void generateToken_savesTokenAndSendsEmail_whenNoExistingToken() {
        User u = user();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(u));
        when(tokenRepository.findByUser(u)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponseDTO<Void>> response =
                emailVerificationService.generateToken(u);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(emailVerificationProducer).send(argThat(event ->
                event.getEmail().equals("alice@example.com") &&
                event.getVerificationLink().contains("http://localhost:8080/")
        ));
    }

    @Test
    void generateToken_returns400_whenValidTokenAlreadyExists() {
        User u = user();
        EmailVerificationToken existing = token(u, LocalDateTime.now().plusHours(12));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(u));
        when(tokenRepository.findByUser(u)).thenReturn(Optional.of(existing));

        ResponseEntity<ApiResponseDTO<Void>> response =
                emailVerificationService.generateToken(u);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("already sent");
        verify(tokenRepository, never()).save(any());
        verify(emailVerificationProducer, never()).send(any());
    }

    @Test
    void generateToken_deletesExpiredToken_andCreatesNew() {
        User u = user();
        EmailVerificationToken expired = token(u, LocalDateTime.now().minusHours(1));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(u));
        when(tokenRepository.findByUser(u)).thenReturn(Optional.of(expired));

        ResponseEntity<ApiResponseDTO<Void>> response =
                emailVerificationService.generateToken(u);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(tokenRepository).delete(expired);
        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(emailVerificationProducer).send(any(EmailVerificationEvent.class));
    }
}
