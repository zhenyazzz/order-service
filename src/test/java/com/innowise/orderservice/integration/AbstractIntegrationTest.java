package com.innowise.orderservice.integration;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    protected WebTestClient webTestClient;

    @BeforeEach
    void setUpWebTestClient() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:8")
            .withExposedPorts(6379);

    protected static WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("apache/kafka-native:3.9.2")
    );

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", wireMock::baseUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
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
        wireMock.stubFor(get(urlEqualTo("/users/internal/" + userId))
                .willReturn(okJson(userResponseJson(userId, name, email))));
    }

    protected void stubInternalUsersByIds(String jsonArrayBody) {
        wireMock.stubFor(post(urlEqualTo("/users/internal/by-ids"))
                .willReturn(okJson(jsonArrayBody)));
    }

    protected void verifyUserByIdCalled(UUID userId) {
        wireMock.verify(getRequestedFor(urlEqualTo("/users/internal/" + userId)));
    }

    protected void verifyUsersByIdsCalled() {
        wireMock.verify(postRequestedFor(urlEqualTo("/users/internal/by-ids")));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTestConfig {
        @Bean
        NewTopic paymentEventsTopic() {
            return TopicBuilder.name("test-payment-events")
                    .partitions(1)
                    .replicas(1)
                    .build();
        }
    }
}
