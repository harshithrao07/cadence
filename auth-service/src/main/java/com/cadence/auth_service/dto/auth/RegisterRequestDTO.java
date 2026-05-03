package com.cadence.auth_service.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequestDTO(
        @NotBlank(message = "Name cannot be blank") String name,
        @NotBlank(message = "Email cannot be blank") String email,
        @NotBlank(message = "Password cannot be blank") String password
) {
}
