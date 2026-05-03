package com.cadence.auth_service.auth;

import com.cadence.auth_service.model.User;
import com.cadence.auth_service.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2SuccessHandlerTest {

    @Mock OAuthUserService oAuthUserService;
    @Mock JwtUtil jwtUtil;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock Authentication authentication;
    @Mock OAuth2User oAuth2User;

    @InjectMocks CustomOAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "frontendUrl", "https://cadence.test");
    }

    @Test
    void onAuthenticationSuccess_resolvesOrCreatesUser_andRedirectsWithTokens() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn("alice@example.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Alice");
        when(oAuth2User.getAttribute("picture")).thenReturn("https://google/pic");
        User user = User.builder().id("user-1").email("alice@example.com").name("Alice").build();
        when(oAuthUserService.findOrCreateUser("alice@example.com", "Alice", "https://google/pic"))
                .thenReturn(user);
        when(jwtUtil.generateToken(eq(user), eq(15L))).thenReturn("access-jwt");
        when(jwtUtil.generateToken(eq(user), eq(7L * 24 * 60))).thenReturn("refresh-jwt");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(oAuthUserService).findOrCreateUser("alice@example.com", "Alice", "https://google/pic");
        verify(jwtUtil).generateToken(user, 15);
        verify(jwtUtil).generateToken(user, 7L * 24 * 60);
        verify(response).sendRedirect(
                "https://cadence.test/auth/success?token=access-jwt&refresh=refresh-jwt&userId=user-1"
        );
    }

    @Test
    void onAuthenticationSuccess_propagatesNullAttributes_toFindOrCreateUser() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn("bob@example.com");
        when(oAuth2User.getAttribute("name")).thenReturn(null);
        when(oAuth2User.getAttribute("picture")).thenReturn(null);
        User user = User.builder().id("user-2").email("bob@example.com").build();
        when(oAuthUserService.findOrCreateUser("bob@example.com", null, null)).thenReturn(user);
        when(jwtUtil.generateToken(eq(user), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("token");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(oAuthUserService).findOrCreateUser("bob@example.com", null, null);
        verify(response).sendRedirect(org.mockito.ArgumentMatchers.contains("userId=user-2"));
    }
}
