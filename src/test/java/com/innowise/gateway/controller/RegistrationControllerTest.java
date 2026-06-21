package com.innowise.gateway.controller;

import com.innowise.gateway.dto.GlobalErrorResponse;
import com.innowise.gateway.dto.RegistrationRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class RegistrationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    private static MockWebServer userServiceServer;
    private static MockWebServer authServiceServer;

    @BeforeAll
    static void setUp() throws IOException {
        userServiceServer = new MockWebServer();
        userServiceServer.start();
        authServiceServer = new MockWebServer();
        authServiceServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        userServiceServer.shutdown();
        authServiceServer.shutdown();
    }

    @DynamicPropertySource
    static void backendProperties(DynamicPropertyRegistry registry) {
        registry.add("services.user-service.url", () -> "http://localhost:" + userServiceServer.getPort());
        registry.add("services.auth-service.url", () -> "http://localhost:" + authServiceServer.getPort());
    }

    @Test
    void register_Success() throws Exception {
        RegistrationRequest request = new RegistrationRequest("Ivan", "Ivanov", "ivan@example.com", "password123");

        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":123,\"name\":\"Ivan\",\"surname\":\"Ivanov\"}"));

        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(201));

        webTestClient.post()
                .uri("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        RecordedRequest userReq = userServiceServer.takeRequest();
        assertEquals("POST", userReq.getMethod());
        assertEquals("/api/users", userReq.getPath());

        RecordedRequest authReq = authServiceServer.takeRequest();
        assertEquals("POST", authReq.getMethod());
        assertEquals("/api/auth/credentials", authReq.getPath());
    }

    @Test
    void register_AuthServiceFails_RollbackSuccess() throws Exception {
        RegistrationRequest request = new RegistrationRequest("Ivan", "Ivanov", "ivan@example.com", "password123");

        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":123,\"name\":\"Ivan\",\"surname\":\"Ivanov\"}"));

        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(500));
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(200));

        webTestClient.post()
                .uri("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(GlobalErrorResponse.class)
                .value(errorResponse -> {
                    assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
                    assertEquals("Registration failed inside Auth Service. User has been deactivated.", errorResponse.getMessage());
                });

        userServiceServer.takeRequest();

        RecordedRequest rollbackReq = userServiceServer.takeRequest();
        assertEquals("PATCH", rollbackReq.getMethod());
        assertEquals("/api/users/123/active?active=false", rollbackReq.getPath());
    }

    @Test
    void register_UserServiceFails() {
        RegistrationRequest request = new RegistrationRequest("Ivan", "Ivanov", "ivan@example.com", "password123");

        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(500));

        webTestClient.post()
                .uri("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(GlobalErrorResponse.class)
                .value(errorResponse -> {
                    assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
                    assertEquals("Registration failed inside User Service.", errorResponse.getMessage());
                });
    }
}