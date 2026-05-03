package com.cadence.auth_service.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtValidationFilterTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    private JwtValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtValidationFilter(authenticationManager);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_setsAuthentication_whenBearerTokenIsValid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        Authentication authResult = new UsernamePasswordAuthenticationToken(
                "alice@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(authenticationManager.authenticate(any(JwtAuthenticationToken.class))).thenReturn(authResult);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<JwtAuthenticationToken> captor = ArgumentCaptor.forClass(JwtAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("good-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authResult);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_doesNotSetAuthentication_whenAuthenticationManagerSaysNotAuthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        Authentication notAuthenticated = new UsernamePasswordAuthenticationToken("x", null);
        notAuthenticated.setAuthenticated(false);
        when(authenticationManager.authenticate(any(JwtAuthenticationToken.class))).thenReturn(notAuthenticated);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_skipsAuthentication_whenAuthorizationHeaderMissing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationManager, never()).authenticate(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_skipsAuthentication_whenAuthorizationHeaderHasNoBearerPrefix() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationManager, never()).authenticate(any());
        verify(filterChain).doFilter(request, response);
    }
}
