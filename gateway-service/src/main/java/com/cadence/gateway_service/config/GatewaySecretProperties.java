package com.cadence.gateway_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gateway")
@Getter
@Setter
public class GatewaySecretProperties {
    private String secret;
}
