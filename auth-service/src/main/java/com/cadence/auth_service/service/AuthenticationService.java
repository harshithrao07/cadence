package com.cadence.auth_service.service;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.auth.*;
import com.cadence.auth_service.events.UserCreatedEvent;
import com.cadence.auth_service.model.*;
import com.cadence.auth_service.producers.UserCreatedProducer;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.auth_service.utils.JwtUtil;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserCreatedProducer producer;
    private final JwtUtil jwtUtil;

    @Transactional
    public ResponseEntity<ApiResponseDTO<AuthenticationResponseDTO>> register(@NotNull RegisterRequestDTO registerRequestDTO) {
        try {
            if (userRepository.existsByEmail(registerRequestDTO.email())) {
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

            User user = User.builder()
                    .name(registerRequestDTO.name())
                    .email(registerRequestDTO.email())
                    .passwordHash(passwordEncoder.encode(password))
                    .build();

            User savedUser = userRepository.save(user);
            producer.send(new UserCreatedEvent(savedUser.getId()));

            if (savedUser.getId() == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred while creating the user", null));
            }

            String accessToken = jwtUtil.generateToken(savedUser, 15);
            String refreshToken = jwtUtil.generateToken(savedUser, 7L * 24 * 60);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDTO<>(
                            true,
                            "User registered successfully",
                            new AuthenticationResponseDTO(savedUser.getId(), accessToken, refreshToken)
                    ));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
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
