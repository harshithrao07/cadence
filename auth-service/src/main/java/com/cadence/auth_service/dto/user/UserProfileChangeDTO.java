package com.cadence.auth_service.dto.user;

import java.util.Optional;

public record UserProfileChangeDTO(
        Optional<String> name
) {
}
