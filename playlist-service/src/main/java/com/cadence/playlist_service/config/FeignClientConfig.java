package com.cadence.playlist_service.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor gatewaySecretInterceptor(GatewaySecretProperties gatewayProperties) {
        return template -> template.header("X-Gateway-Secret", gatewayProperties.getSecret());
    }
}
