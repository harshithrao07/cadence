package com.project.cadence.filter;

import com.project.cadence.config.GatewaySecretProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class InternalTrafficFilter extends OncePerRequestFilter {

    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    private final GatewaySecretProperties gatewayProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/actuator/") || path.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String secretHeader = request.getHeader(GATEWAY_SECRET_HEADER);

        if (secretHeader == null || !secretHeader.equals(gatewayProperties.getSecret())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Direct access forbidden\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
