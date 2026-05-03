package com.cadence.gateway_service.filter;

import com.cadence.gateway_service.config.GatewaySecretProperties;
import com.cadence.gateway_service.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/auth/",
            "/app/",
            "/oauth2/",
            "/login/"
    );

    private final JwtUtil jwtUtil;
    private final GatewaySecretProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange cleanExchange = removeClientIdentityHeaders(exchange);

        if (isPublicRequest(cleanExchange)) {
            // Public path: no JWT required, but we still stamp X-Gateway-Secret so
            // the backend's InternalTrafficFilter knows this came through the gateway
            // and not a direct hit on the backend port.
            return chain.filter(addGatewaySecret(cleanExchange));
        }

        String token = extractBearerToken(cleanExchange.getRequest().getHeaders());
        if (token == null) {
            return unauthorized(cleanExchange);
        }

        JwtUtil.JwtIdentity identity = jwtUtil.validateAndExtractIdentity(token);
        if (identity == null) {
            return unauthorized(cleanExchange);
        }

        return chain.filter(addIdentityHeaders(cleanExchange, identity));
    }

    private ServerWebExchange addGatewaySecret(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(GATEWAY_SECRET_HEADER, gatewayProperties.getSecret())
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublicRequest(ServerWebExchange exchange) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return true;
        }

        String path = exchange.getRequest().getURI().getPath();
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractBearerToken(HttpHeaders headers) {
        String authorizationHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    private ServerWebExchange removeClientIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    headers.remove(GATEWAY_SECRET_HEADER);
                })
                .build();

        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange addIdentityHeaders(ServerWebExchange exchange, JwtUtil.JwtIdentity identity) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(USER_ID_HEADER, identity.userId())
                .header(USER_EMAIL_HEADER, identity.email())
                .header(USER_ROLE_HEADER, identity.role())
                .header(GATEWAY_SECRET_HEADER, gatewayProperties.getSecret())
                .build();

        return exchange.mutate().request(request).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        byte[] body = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
