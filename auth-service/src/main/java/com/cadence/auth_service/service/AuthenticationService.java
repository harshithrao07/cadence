package com.cadence.auth_service.service;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.auth.*;
import com.cadence.auth_service.model.*;
import com.cadence.auth_service.saga.RegistrationSaga;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.utils.JwtUtil;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationSaga registrationSaga;
    private final JwtUtil jwtUtil;

    /** How long register waits for the registration saga before answering 202. */
    @Value("${cadence.registration.await-timeout:PT5S}")
    private Duration registrationAwaitTimeout;

    /**
     * Starts the registration saga, then waits briefly for it so the usual case still answers 201 with tokens:
     * <ul>
     *     <li>ACTIVE in time: 201 + tokens</li>
     *     <li>still PENDING: 202 without tokens; the user can log in once setup finishes</li>
     *     <li>FAILED: 503; the user may try again</li>
     * </ul>
     * Not transactional: the saga's transaction must commit before waiting.
     */
    public ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> register(@NotNull RegisterRequestDTO registerRequestDTO) {
        try {
            Optional<User> existing = userRepository.findByEmail(registerRequestDTO.email());
            if (existing.isPresent() && existing.get().getStatus() != UserStatus.FAILED) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ApiResponseDTO<>(false, "A user with the given email already exists", null));
            }

            String password = registerRequestDTO.password();
            if (password.isBlank() ||
                    password.length() < 10 ||
                    !password.matches(".*([0-9]|[!@#$%^&*(),.?\":{}|<>]).*")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponseDTO<>(false, "Password must be at least 10 characters long and contain at least one special character", null));
            }

            // A FAILED registration is retried on the same row.
            User user = existing.orElseGet(User::new);
            user.setName(registerRequestDTO.name());
            user.setEmail(registerRequestDTO.email());
            user.setPasswordHash(passwordEncoder.encode(password));
            if (user.getRole() == null) {
                user.setRole(Role.USER);
            }
            User savedUser = registrationSaga.start(user);

            UserStatus outcome = registrationSaga.awaitOutcome(savedUser.getId(), registrationAwaitTimeout);
            return switch (outcome) {
                case ACTIVE -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new ApiResponseDTO<>(
                                true,
                                "User registered successfully",
                                tokensFor(savedUser)
                        ));
                case PENDING -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(new ApiResponseDTO<>(
                                true,
                                "Your account is being set up. You can log in in a moment.",
                                new AuthenticationResponseDTO(savedUser.getId(), null, null)
                        ));
                case FAILED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ApiResponseDTO<>(false, "We couldn't finish setting up your account. Please try again.", null));
            };
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    private AuthenticationResponseDTO tokensFor(User user) {
        return new AuthenticationResponseDTO(
                user.getId(),
                jwtUtil.generateToken(user, 15),
                jwtUtil.generateToken(user, 7L * 24 * 60)
        );
    }

    public ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> authenticate(AuthenticateRequestDTO authenticateRequestDTO) {
        try {
            Optional<User> user = userRepository.findByEmail(authenticateRequestDTO.email());
            if (user.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseDTO<>(false, "User does not exist", null));
            }

            if (!passwordEncoder.matches(authenticateRequestDTO.password(), user.get().getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseDTO<>(false, "Password does not match", null));
            }

            if (user.get().getStatus() == UserStatus.PENDING) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponseDTO<>(false, "Your account is still being set up. Please try again in a moment.", null));
            }
            if (user.get().getStatus() == UserStatus.FAILED) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponseDTO<>(false, "Account setup failed. Please register again.", null));
            }

            String accessToken = jwtUtil.generateToken(user.get(), 15);
            String refreshToken = jwtUtil.generateToken(user.get(), 7L * 24 * 60);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDTO<>(
                            true,
                            "User registered successfully",
                            new AuthenticationResponseDTO(user.get().getId(), accessToken, refreshToken)
                    ));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }

    public ResponseEntity<ApiResponseDTO<Boolean>> validateEmail(@Valid String email) {
        try {
            boolean exists = userRepository.existsByEmail(email);
            String message = exists ? "User already exists" : "User does not exist";
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, message, exists));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }
}
