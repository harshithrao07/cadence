package com.cadence.auth_service.integration;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.auth.AuthenticateRequestDTO;
import com.cadence.auth_service.dto.auth.AuthenticationResponseDTO;
import com.cadence.auth_service.dto.auth.RegisterRequestDTO;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.service.AuthenticationService;
import com.cadence.auth_service.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationServiceIT extends BaseIntegrationTest {

    @Autowired AuthenticationService authenticationService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private static final String EMAIL = "alice@example.com";
    private static final String NAME = "Alice";
    private static final String STRONG_PASSWORD = "StrongPass1!";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_persistsUser_withHashedPassword_andReturnsValidTokens() {
        RegisterRequestDTO req = new RegisterRequestDTO(NAME, EMAIL, STRONG_PASSWORD);

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> result =
                authenticationService.register(req);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthenticationResponseDTO body = result.getBody().data();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotBlank();
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();

        User saved = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getName()).isEqualTo(NAME);
        assertThat(saved.getPasswordHash()).isNotEqualTo(STRONG_PASSWORD);
        assertThat(passwordEncoder.matches(STRONG_PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.isEmailVerified()).isFalse();

        JwtUtil.JwtIdentity identity = jwtUtil.validateAndExtractIdentity(body.accessToken());
        assertThat(identity).isNotNull();
        assertThat(identity.email()).isEqualTo(EMAIL);
        assertThat(identity.userId()).isEqualTo(saved.getId());
    }

    @Test
    void register_returns409_whenEmailAlreadyExists() {
        userRepository.save(User.builder()
                .name("Existing")
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(STRONG_PASSWORD))
                .build());

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> result =
                authenticationService.register(new RegisterRequestDTO(NAME, EMAIL, STRONG_PASSWORD));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.getBody().message()).contains("already exists");
    }

    @Test
    void register_returns400_whenPasswordIsTooShort() {
        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> result =
                authenticationService.register(new RegisterRequestDTO(NAME, EMAIL, "short1!"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void register_returns400_whenPasswordLacksSpecialOrDigit() {
        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> result =
                authenticationService.register(new RegisterRequestDTO(NAME, EMAIL, "alllowercase"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void authenticate_returns201_andTokens_forValidCredentials() {
        authenticationService.register(new RegisterRequestDTO(NAME, EMAIL, STRONG_PASSWORD));

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> result =
                authenticationService.authenticate(new AuthenticateRequestDTO(EMAIL, STRONG_PASSWORD));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthenticationResponseDTO body = result.getBody().data();
        assertThat(body.accessToken()).isNotBlank();
        assertThat(jwtUtil.validateAndExtractIdentity(body.accessToken()).email()).isEqualTo(EMAIL);
    }

    @Test
    void authenticate_returns400_forWrongPassword() {
        authenticationService.register(new RegisterRequestDTO(NAME, EMAIL, STRONG_PASSWORD));

        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> result =
                authenticationService.authenticate(new AuthenticateRequestDTO(EMAIL, "WrongPass1!"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().message()).isEqualTo("Password does not match");
    }

    @Test
    void authenticate_returns400_forUnknownEmail() {
        ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> result =
                authenticationService.authenticate(new AuthenticateRequestDTO("ghost@example.com", STRONG_PASSWORD));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().message()).isEqualTo("User does not exist");
    }

    @Test
    void validateEmail_returnsTrue_whenEmailExists() {
        authenticationService.register(new RegisterRequestDTO(NAME, EMAIL, STRONG_PASSWORD));

        ResponseEntity<ApiResponseDTO<Boolean>> result = authenticationService.validateEmail(EMAIL);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isTrue();
    }

    @Test
    void validateEmail_returnsFalse_whenEmailDoesNotExist() {
        ResponseEntity<ApiResponseDTO<Boolean>> result = authenticationService.validateEmail("ghost@example.com");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isFalse();
    }
}
