package com.innowise.gateway.filter;

import com.innowise.gateway.dto.ValidateTokenRequest;
import com.innowise.gateway.dto.ValidateTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final WebClient.Builder webClientBuilder;
    private final String authServiceUrl;

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/token",
            "/api/register"
    );

    public JwtAuthenticationFilter(WebClient.Builder webClientBuilder,
                                   @Value("${services.auth-service.url}") String authServiceUrl) {
        this.webClientBuilder = webClientBuilder;
        this.authServiceUrl = authServiceUrl;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (EXCLUDED_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        WebClient authServiceClient = webClientBuilder.baseUrl(authServiceUrl).build();

        return authServiceClient.post()
                .uri("/api/auth/validate")
                .bodyValue(new ValidateTokenRequest(token))
                .retrieve()
                .bodyToMono(ValidateTokenResponse.class)
                .flatMap(response -> {
                    if (response != null && response.isValid()) {
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", String.valueOf(response.getUserId()))
                                .header("X-User-Email", response.getEmail())
                                .header("X-User-Roles", String.join(",", response.getRoles()))
                                .build();
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    } else {
                        return onError(exchange, HttpStatus.UNAUTHORIZED);
                    }
                })
                .onErrorResume(e -> onError(exchange, HttpStatus.UNAUTHORIZED));
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}