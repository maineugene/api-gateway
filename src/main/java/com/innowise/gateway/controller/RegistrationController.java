package com.innowise.gateway.controller;

import com.innowise.gateway.dto.RegistrationRequest;
import com.innowise.gateway.dto.SaveCredentialsRequest;
import com.innowise.gateway.dto.UserRequestDto;
import com.innowise.gateway.dto.UserResponseDto;
import com.innowise.gateway.exception.RegistrationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/register")
public class RegistrationController {

    private final WebClient.Builder webClientBuilder;
    private final String userServiceUrl;
    private final String authServiceUrl;

    public RegistrationController(WebClient.Builder webClientBuilder,
                                  @Value("${services.user-service.url}") String userServiceUrl,
                                  @Value("${services.auth-service.url}") String authServiceUrl) {
        this.webClientBuilder = webClientBuilder;
        this.userServiceUrl = userServiceUrl;
        this.authServiceUrl = authServiceUrl;
    }

    @PostMapping
    public Mono<ResponseEntity<Void>> register(@RequestBody RegistrationRequest request) {
        WebClient userServiceClient = webClientBuilder.baseUrl(userServiceUrl).build();
        WebClient authServiceClient = webClientBuilder.baseUrl(authServiceUrl).build();

        UserRequestDto userRequest = new UserRequestDto(request.getName(), request.getSurname());

        return userServiceClient.post()
                .uri("/api/users")
                .bodyValue(userRequest)
                .retrieve()
                .bodyToMono(UserResponseDto.class)
                .flatMap(userResponse -> {
                    Long userId = userResponse.getId();
                    SaveCredentialsRequest credentialsRequest = new SaveCredentialsRequest(userId, request.getEmail(), request.getPassword());

                    return authServiceClient.post()
                            .uri("/api/auth/credentials")
                            .bodyValue(credentialsRequest)
                            .retrieve()
                            .toBodilessEntity()
                            .map(response -> ResponseEntity.status(HttpStatus.CREATED).<Void>build())
                            .onErrorResume(error -> userServiceClient.patch()
                                    .uri(uriBuilder -> uriBuilder
                                            .path("/api/users/{id}/active")
                                            .queryParam("active", false)
                                            .build(userId))
                                    .retrieve()
                                    .toBodilessEntity()
                                    .flatMap(rollbackResponse -> Mono.error(new RegistrationException("Registration failed inside Auth Service. User has been deactivated.", error))));
                })
                .onErrorResume(error -> {
                    if (error instanceof RegistrationException) {
                        return Mono.error(error);
                    }
                    return Mono.error(new RegistrationException("Registration failed inside User Service.", error));
                });
    }
}