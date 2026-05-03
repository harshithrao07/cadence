package com.cadence.auth_service.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stamps {@code X-Gateway-Secret} on every outbound Feign request so the destination
 * service's {@code InternalTrafficFilter} accepts it as legitimate cross-service traffic.
 *
 * <p>Without this, service-to-service calls would be rejected with 403, the
 * circuit breaker fallback would silently return an empty list, and the caller
 * would render an incomplete page with no error visible to the user.
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor gatewaySecretInterceptor(GatewaySecretProperties gatewayProperties) {
        return template -> template.header("X-Gateway-Secret", gatewayProperties.getSecret());
    }
}
