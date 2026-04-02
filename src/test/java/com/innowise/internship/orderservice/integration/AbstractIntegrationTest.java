package com.innowise.internship.orderservice.integration;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import tools.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Testcontainers
@ActiveProfiles("integrationtest")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final WireMockServer WIRE_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        WIRE_MOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(6379);

    @BeforeAll
    static void startRedis() {
        redisContainer.start();
    }

    @AfterAll
    static void stopRedis() {
        redisContainer.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("user-service.url", () -> "http://localhost:" + WIRE_MOCK.port());

        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    protected WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        WIRE_MOCK.resetAll();

        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    protected static String userResponseJson(UUID id, String name, String email) {
        return """
                {
                  "id": "%s",
                  "name": "%s",
                  "surname": "User",
                  "birthDate": null,
                  "email": "%s"
                }
                """.formatted(id, name, email);
    }

    protected void stubInternalUserById(UUID userId, String name, String email) {
        WIRE_MOCK.stubFor(get(urlEqualTo("/users/internal/" + userId))
                .willReturn(okJson(userResponseJson(userId, name, email))));
    }

    protected void stubInternalUsersByIds(String jsonArrayBody) {
        WIRE_MOCK.stubFor(post(urlEqualTo("/users/internal/by-ids"))
                .willReturn(okJson(jsonArrayBody)));
    }

    protected void verifyUserByIdCalled(UUID userId) {
        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/users/internal/" + userId)));
    }
    protected void verifyUsersByIdsCalled() {
        WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/users/internal/by-ids")));
    }
}
