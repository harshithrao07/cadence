package com.cadence.auth_service.controller;

import com.cadence.auth_service.auth.SecurityConfig;
import com.cadence.auth_service.dto.ApiResponseDTO;
import com.cadence.auth_service.dto.auth.AuthenticateRequestDTO;
import com.cadence.auth_service.dto.auth.AuthenticationResponseDTO;
import com.cadence.auth_service.dto.auth.RegisterRequestDTO;
import com.cadence.auth_service.filter.InternalTrafficFilter;
import com.cadence.auth_service.service.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthenticationController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {InternalTrafficFilter.class, SecurityConfig.class}
        )
)
class AuthenticationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthenticationService authenticationService;

    private AuthenticationResponseDTO tokens() {
        return new AuthenticationResponseDTO("user-1", "access-token", "refresh-token");
    }

    // ── POST /auth/v1/register ────────────────────────────────────────────────

    @Test
    void register_returns201_andTokens_onSuccess() throws Exception {
        RegisterRequestDTO req = new RegisterRequestDTO("Alice", "alice@example.com", "ValidPass1!");
        when(authenticationService.register(any())).thenReturn(
                ResponseEntity.status(201).body(new ApiResponseDTO<>(true, "Registered", tokens()))
        );

        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.id").value("user-1"));
    }

    @Test
    void register_returns400_whenNameIsBlank() throws Exception {
        RegisterRequestDTO req = new RegisterRequestDTO("", "alice@example.com", "ValidPass1!");

        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400_whenEmailIsBlank() throws Exception {
        RegisterRequestDTO req = new RegisterRequestDTO("Alice", "", "ValidPass1!");

        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns409_whenEmailAlreadyExists() throws Exception {
        RegisterRequestDTO req = new RegisterRequestDTO("Alice", "alice@example.com", "ValidPass1!");
        when(authenticationService.register(any())).thenReturn(
                ResponseEntity.status(409).body(new ApiResponseDTO<>(false, "Email already exists", null))
        );

        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST /auth/v1/authenticate ────────────────────────────────────────────

    @Test
    void authenticate_returns201_andTokens_onSuccess() throws Exception {
        AuthenticateRequestDTO req = new AuthenticateRequestDTO("alice@example.com", "ValidPass1!");
        when(authenticationService.authenticate(any())).thenReturn(
                ResponseEntity.status(201).body(new ApiResponseDTO<>(true, "Authenticated", tokens()))
        );

        mockMvc.perform(post("/auth/v1/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void authenticate_returns400_whenEmailIsBlank() throws Exception {
        AuthenticateRequestDTO req = new AuthenticateRequestDTO("", "pass");

        mockMvc.perform(post("/auth/v1/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticate_returns400_whenCredentialsAreWrong() throws Exception {
        AuthenticateRequestDTO req = new AuthenticateRequestDTO("alice@example.com", "WrongPass!");
        when(authenticationService.authenticate(any())).thenReturn(
                ResponseEntity.badRequest().body(new ApiResponseDTO<>(false, "Password does not match", null))
        );

        mockMvc.perform(post("/auth/v1/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password does not match"));
    }
}
