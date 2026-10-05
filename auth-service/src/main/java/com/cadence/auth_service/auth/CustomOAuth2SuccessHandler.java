package com.cadence.auth_service.auth;

import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.saga.RegistrationSaga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cadence.auth_service.dto.auth.AuthenticationResponseDTO;
import com.cadence.auth_service.model.User;
import com.cadence.auth_service.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {
    private final OAuthUserService oAuthUserService;
    private final JwtUtil jwtUtil;
    private final RegistrationSaga registrationSaga;

    @Value("${cadence.registration.await-timeout:PT5S}")
    private java.time.Duration registrationAwaitTimeout;
    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        User user = oAuthUserService.findOrCreateUser(email, name, picture);

        UserStatus status = user.getStatus() == UserStatus.ACTIVE
                ? UserStatus.ACTIVE
                : registrationSaga.awaitOutcome(user.getId(), registrationAwaitTimeout);
        if (status != UserStatus.ACTIVE) {
            String reason = status == UserStatus.PENDING ? "account_setup_pending" : "account_setup_failed";
            response.sendRedirect(frontendUrl + "/auth/login?error=" + reason);
            return;
        }

        String accessToken = jwtUtil.generateToken(user, 15);
        String refreshToken = jwtUtil.generateToken(user, 7L * 24 * 60);

        response.sendRedirect(
                frontendUrl + "/auth/success?token=" + accessToken + "&refresh=" + refreshToken + "&userId=" + user.getId()
        );
    }
}
