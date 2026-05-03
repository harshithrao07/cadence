package com.cadence.auth_service.service;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.events.EmailVerificationEvent;
import com.cadence.auth_service.model.EmailVerificationToken;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.producers.EmailVerificationProducer;
import com.cadence.auth_service.repository.EmailVerificationTokenRepository;
import com.cadence.auth_service.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    @Value("${backend.url}")
    private String backendUrl;
    private final EmailVerificationProducer emailVerificationProducer;

    @Transactional
    public ResponseEntity<?> verifyEmail(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                        .orElseThrow(() ->
                                new RuntimeException("Invalid token"));

        if (verificationToken.getExpiryDate()
                .isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest()
                    .body("Token expired");
        }

        User user = verificationToken.getUser();

        if (user.isEmailVerified()) {
            return ResponseEntity.ok("Email already verified");
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        tokenRepository.delete(verificationToken);
        return ResponseEntity.ok("Email verified successfully");
    }

    public ResponseEntity<ApiResponseDTO<Void>> generateToken(UserDetails userDetails) {
        try {
            String email = userDetails.getUsername();
            User user = userRepository.findByEmail(email).orElseThrow();

            Optional<EmailVerificationToken> existingTokenOpt =
                    tokenRepository.findByUser(user);

            if (existingTokenOpt.isPresent()) {
                EmailVerificationToken existingToken = existingTokenOpt.get();

                if (existingToken.getExpiryDate().isAfter(LocalDateTime.now())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ApiResponseDTO<>(
                                    false,
                                    "Verification email already sent. Please check your inbox.",
                                    null
                            ));
                }

                tokenRepository.delete(existingToken);
            }

            String token = UUID.randomUUID().toString();

            EmailVerificationToken verificationToken =
                    EmailVerificationToken.builder()
                            .token(token)
                            .user(user)
                            .expiryDate(LocalDateTime.now().plusHours(24))
                            .build();

            tokenRepository.save(verificationToken);

            String verificationLink = backendUrl + "app/v1/verify-email?token=" + token;
            emailVerificationProducer.send(new EmailVerificationEvent(user.getEmail(), verificationLink));

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseDTO<>(true, "Sent verification email successfully", null));
        } catch (Exception e) {
            log.error("An exception has occurred {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponseDTO<>(false, "An error occurred in the server", null));
        }
    }
}
