package com.cadence.streaming_service.filter;

import com.cadence.streaming_service.config.GatewaySecretProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalTrafficFilterTest {

    private static final String VALID_SECRET = "test-secret";

    private FilterChain filterChain;
    private InternalTrafficFilter filter;

    @BeforeEach
    void setUp() {
        filterChain = mock(FilterChain.class);
        GatewaySecretProperties props = new GatewaySecretProperties();
        props.setSecret(VALID_SECRET);
        filter = new InternalTrafficFilter(props);
    }

    // ── always-allowed paths ──────────────────────────────────────────────────

    @Test
    void actuatorPath_passesThrough_withoutSecretCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void internalPath_passesThrough_withoutSecretCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/history/user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ── secret validation ─────────────────────────────────────────────────────

    @Test
    void validSecret_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trending");
        request.addHeader("X-Gateway-Secret", VALID_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void missingSecret_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trending");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("Direct access forbidden");
    }

    @Test
    void wrongSecret_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trending");
        request.addHeader("X-Gateway-Secret", "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }
}
