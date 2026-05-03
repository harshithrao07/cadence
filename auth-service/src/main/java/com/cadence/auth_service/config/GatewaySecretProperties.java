package com.cadence.auth_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed binding for {@code gateway.*} properties.
 *
 * <p>Sourced from {@code centralconfigs/global/application.properties}. After editing
 * the value at the config server, POST to {@code /actuator/refresh} on this service
 * and Spring Cloud rebinds the field — the {@code InternalTrafficFilter} that holds
 * this bean reads the updated secret on the next request, no restart needed.
 */
@Component
@ConfigurationProperties(prefix = "gateway")
@Getter
@Setter
public class GatewaySecretProperties {
    private String secret;
}
