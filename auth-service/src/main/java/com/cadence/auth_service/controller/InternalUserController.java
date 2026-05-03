package com.cadence.auth_service.controller;

import com.cadence.auth_service.dto.user.UserPreviewDTO;
import com.cadence.auth_service.dto.user.UserIdentityDTO;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalUserController {
    private final UserRepository userRepository;

    @GetMapping("/users/{userId}/preview")
    public ResponseEntity<UserPreviewDTO> getUserPreview(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(new UserPreviewDTO(
                user.getId(),
                user.getName(),
                user.getProfileUrl()
        ));
    }

    @GetMapping("/users/by-email")
    public ResponseEntity<UserIdentityDTO> getUserIdentityByEmail(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(new UserIdentityDTO(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        ));
    }
}
