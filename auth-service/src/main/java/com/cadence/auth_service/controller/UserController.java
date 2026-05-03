package com.cadence.auth_service.controller;

import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.user.UserProfileChangeDTO;
import com.cadence.auth_service.dto.user.UserProfileDTO;
import com.cadence.auth_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user")
public class UserController {
    private final UserService userService;

    @GetMapping(path = "/{userId}")
    public ResponseEntity<ApiResponseDTO<UserProfileDTO>> getUserProfile(@AuthenticationPrincipal UserDetails userDetails, @PathVariable("userId") String userId) {
        return userService.getUserProfile(userId, userDetails.getUsername());
    }

    @PutMapping(path = "/{userId}")
    public ResponseEntity<ApiResponseDTO<Void>> putUserProfile(@AuthenticationPrincipal UserDetails userDetails, @PathVariable("userId") String userId, @RequestBody UserProfileChangeDTO userProfileChangeDTO) {
        return userService.putUserProfile(userId, userProfileChangeDTO, userDetails.getUsername());
    }

    @GetMapping(path = "/isAdmin")
    public ResponseEntity<Boolean> isAdmin(@AuthenticationPrincipal UserDetails userDetails) {
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return ResponseEntity.status(HttpStatus.OK).body(true);
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(false);
        }
    }
}
