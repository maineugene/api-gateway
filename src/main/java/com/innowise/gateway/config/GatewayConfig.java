package com.innowise.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/auth/token", "/api/auth/validate", "/api/auth/refresh", "/api/auth/credentials/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("authService")
                                        .setFallbackUri("forward:/fallback/auth")))
                        .uri("lb://authservice"))
                .route("user-service", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("userService")
                                        .setFallbackUri("forward:/fallback/user")))
                        .uri("lb://userservice"))
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("orderService")
                                        .setFallbackUri("forward:/fallback/order")))
                        .uri("lb://orderservice"))
                .build();
    }
}