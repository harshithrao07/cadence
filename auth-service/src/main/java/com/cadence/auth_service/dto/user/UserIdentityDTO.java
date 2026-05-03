package com.cadence.auth_service.dto.user;

public record UserIdentityDTO(
        String id,
        String email,
        String role
) {
}
