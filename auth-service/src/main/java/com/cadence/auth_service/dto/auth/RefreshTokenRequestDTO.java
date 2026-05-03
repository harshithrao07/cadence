package com.cadence.auth_service.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDTO(
        @NotBlank(message = "Refresh token cannot be blank") String refreshToken
) {
}
