package com.cadence.auth_service.auth;

import com.cadence.auth_service.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationProviderTest {

    @Mock JwtUtil jwtUtil;
    @Mock UserDetailsService userDetailsService;

    @InjectMocks JwtAuthenticationProvider provider;

    @Test
    void authenticate_returnsAuthenticatedToken_forValidJwt() {
        when(jwtUtil.validateAndExtractUsername("good-token")).thenReturn("alice@example.com");
        UserDetails userDetails = new User("alice@example.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);

        Authentication result = provider.authenticate(new JwtAuthenticationToken("good-token"));

        assertThat(result).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(result.getPrincipal()).isSameAs(userDetails);
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getAuthorities())
                .extracting("authority").containsExactly("ROLE_USER");
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void authenticate_throwsBadCredentials_whenJwtUtilReturnsNull() {
        when(jwtUtil.validateAndExtractUsername("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(new JwtAuthenticationToken("bad-token")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid JWT Token");

        verify(userDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void supports_returnsTrue_forJwtAuthenticationToken() {
        assertThat(provider.supports(JwtAuthenticationToken.class)).isTrue();
    }

    @Test
    void supports_returnsFalse_forUnrelatedAuthenticationType() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }
}
