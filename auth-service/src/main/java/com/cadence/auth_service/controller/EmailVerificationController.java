package com.cadence.auth_service.controller;
import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/email")
public class EmailVerificationController {
    private final EmailVerificationService emailVerificationService;

    @GetMapping(path = "/generate-verification-token")
    public ResponseEntity<ApiResponseDTO<Void>> generateToken(@AuthenticationPrincipal UserDetails userDetails) {
        return emailVerificationService.generateToken(userDetails);
    }
}
