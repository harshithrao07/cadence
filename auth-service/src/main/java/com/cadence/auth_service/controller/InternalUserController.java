package com.cadence.auth_service.controller;

import com.cadence.auth_service.dto.user.UserPreviewDTO;
import com.cadence.auth_service.dto.user.UserIdentityDTO;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.producers.UserUpdatedProducer;
import com.cadence.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalUserController {
    private final UserRepository userRepository;
    private final UserUpdatedProducer userUpdatedProducer;

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

    /**
     * Republishes every ACTIVE user's snapshot, e.g. to (re)build catalog's user_replica. Safe to repeat: replicas upsert.
     * Not routed by the gateway: call it from inside the network, e.g.
     * {@code docker exec cadence-auth curl -X POST localhost:8085/internal/users/republish}.
     */
    @PostMapping("/users/republish")
    @Transactional
    public ResponseEntity<Map<String, Integer>> republishUsers() {
        List<User> users = userRepository.findAllByStatus(UserStatus.ACTIVE);
        users.forEach(userUpdatedProducer::send);
        return ResponseEntity.ok(Map.of("republished", users.size()));
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
