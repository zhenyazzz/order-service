package com.innowise.internship.orderservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.innowise.internship.orderservice.dto.request.CreateOrderRequest;
import com.innowise.internship.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.internship.orderservice.dto.response.OrderResponse;
import com.innowise.internship.orderservice.model.enums.OrderStatus;
import com.innowise.internship.orderservice.utils.OrderTestDataFactory;

@DisplayName("Order API integration tests (Controller → Service → Repository → DB)")
class OrderControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
    private static final UUID ORDER_PENDING_A = UUID.fromString("aaaaaaaa-0001-4001-8001-000000000001");
    private static final UUID ORDER_CONFIRMED_A = UUID.fromString("aaaaaaaa-0002-4002-8002-000000000002");
    private static final UUID ORDER_CANCELLED_B = UUID.fromString("aaaaaaaa-0003-4003-8003-000000000003");

    private void stubSeedUsersBatch() {
        stubInternalUsersByIds("[" + userResponseJson(USER_A, "Test", "buyer@example.com") + ","
                + userResponseJson(USER_B, "Other", "other@example.com") + "]");
    }

    private OrderResponse createOrder(UUID userId, String name, String email, String idempotencyKey) {
        stubInternalUserById(userId, name, email);

        CreateOrderRequest request = OrderTestDataFactory.buildCreateOrderRequest();

        return webTestClient
                .post()
                .uri("/orders")
                .header("X-User-Id", userId.toString())
                .header("X-User-Email", email)
                .header("X-User-Roles", "ROLE_USER")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private String createOrderId(UUID userId, String name, String email) {
        OrderResponse response = createOrder(userId, name, email, UUID.randomUUID().toString());
        assertThat(response).isNotNull();
        return response.id().toString();
    }

    @Nested
    @DisplayName("POST /orders")
    class Create {

        @Test
        @DisplayName("creates order and returns 201 with persisted data")
        void createsOrder_andReturns201WithPersistedData() {
            OrderResponse response = createOrder(USER_A, "Test", OrderTestDataFactory.USER_EMAIL, UUID.randomUUID().toString());

            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.totalPrice()).isEqualByComparingTo("20.00");
            assertThat(response.orderItems()).isNotEmpty();
        }

        @Test
        @DisplayName("when Idempotency-Key is missing returns 400")
        void whenIdempotencyKeyMissing_returns400() {
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .post()
                    .uri("/orders")
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(OrderTestDataFactory.buildCreateOrderRequest())
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("when Idempotency-Key repeats returns cached response")
        void whenSameIdempotencyKey_returnsCachedResponse() {
            String idemKey = UUID.randomUUID().toString();

            OrderResponse first = createOrder(USER_A, "Test", OrderTestDataFactory.USER_EMAIL, idemKey);
            OrderResponse second = createOrder(USER_A, "Test", OrderTestDataFactory.USER_EMAIL, idemKey);

            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(second.id()).isEqualTo(first.id());
            assertThat(second.status()).isEqualTo(first.status());
            assertThat(second.totalPrice()).isEqualByComparingTo(first.totalPrice());
        }
    }

    @Nested
    @DisplayName("GET /orders/me/{id}")
    class GetMyOrderById {

        @Test
        @DisplayName("when order belongs to current user returns 200")
        void whenPendingOrder_returns200WithUserData() {
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .get()
                    .uri("/orders/me/{id}", ORDER_PENDING_A)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(ORDER_PENDING_A);
                        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
                        assertThat(response.user()).isNotNull();
                        assertThat(response.user().id()).isEqualTo(USER_A);
                        assertThat(response.user().email()).isEqualTo(OrderTestDataFactory.USER_EMAIL);
                    });
        }

        @Test
        @DisplayName("when order belongs to another user returns 404")
        void whenOtherUsersOrder_returns404() {
            webTestClient
                    .get()
                    .uri("/orders/me/{id}", ORDER_PENDING_A)
                    .header("X-User-Id", USER_B.toString())
                    .header("X-User-Email", "other@example.com")
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("when order is not PENDING returns 409")
        void whenNotPending_returns409() {
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .get()
                    .uri("/orders/me/{id}", ORDER_CONFIRMED_A)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("GET /orders/{id}")
    class GetOrderById {

        @Test
        @DisplayName("when admin requests order returns 200")
        void whenAdmin_returns200WithUserData() {
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .get()
                    .uri("/orders/{id}", ORDER_PENDING_A)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(ORDER_PENDING_A);
                        assertThat(response.user()).isNotNull();
                        assertThat(response.user().id()).isEqualTo(USER_A);
                    });
        }

        @Test
        @DisplayName("when non-admin requests order returns 403")
        void whenNotAdmin_returns403() {
            webTestClient
                    .get()
                    .uri("/orders/{id}", ORDER_PENDING_A)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /orders")
    class GetOrders {

        @Test
        @DisplayName("when admin requests orders returns page")
        void whenAdmin_returnsPage() {
            stubSeedUsersBatch();

            webTestClient
                    .get()
                    .uri("/orders?page=0&size=20")
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()")
                    .value(length -> assertThat((Integer) length).isPositive());
        }

        @Test
        @DisplayName("when non-admin requests orders returns 403")
        void whenNotAdmin_returns403() {
            webTestClient
                    .get()
                    .uri("/orders?page=0&size=20")
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /orders/user/{userId}")
    class GetOrdersByUserId {

        @Test
        @DisplayName("when admin filters by user returns page")
        void whenAdmin_returnsFilteredPage() {
            stubInternalUsersByIds("[" + userResponseJson(USER_B, "Other", "other@example.com") + "]");

            webTestClient
                    .get()
                    .uri("/orders/user/{userId}?page=0&size=20", USER_B)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content[0].user.id")
                    .isEqualTo(USER_B.toString());
        }

        @Test
        @DisplayName("when non-admin filters by user returns 403")
        void whenNotAdmin_returns403() {
            webTestClient
                    .get()
                    .uri("/orders/user/{userId}?page=0&size=20", USER_B)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /orders/me")
    class GetMyOrders {

        @Test
        @DisplayName("when user requests own orders returns page")
        void whenUser_returnsOwnOrders() {
            stubInternalUsersByIds("[" + userResponseJson(USER_A, "Test", OrderTestDataFactory.USER_EMAIL) + "]");

            webTestClient
                    .get()
                    .uri("/orders/me?page=0&size=20")
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content[0].user.id")
                    .isEqualTo(USER_A.toString());
        }
    }

    @Nested
    @DisplayName("PUT /orders/me/{id}")
    class UpdateMyOrder {

        @Test
        @DisplayName("when PENDING order is updated returns 200")
        void whenPending_returns200() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            UpdateOrderRequest update = OrderTestDataFactory.buildUpdateOrderRequest();

            webTestClient
                    .put()
                    .uri("/orders/me/{id}", orderId)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(update)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(UUID.fromString(orderId));
                        assertThat(response.totalPrice()).isEqualByComparingTo("30.00");
                    });
        }
    }

    @Nested
    @DisplayName("POST /orders/me/{id}/cancel")
    class CancelMyOrder {

        @Test
        @DisplayName("when PENDING order is cancelled returns 200")
        void whenPending_returns200() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .post()
                    .uri("/orders/me/{id}/cancel", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(UUID.fromString(orderId));
                        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
                    });
        }

        @Test
        @DisplayName("when cancelled twice returns 409")
        void whenNotPending_returns409() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .post()
                    .uri("/orders/me/{id}/cancel", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isOk();

            webTestClient
                    .post()
                    .uri("/orders/me/{id}/cancel", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("POST /orders/{id}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("when admin cancels order returns 200")
        void whenAdmin_returns200() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .post()
                    .uri("/orders/{id}/cancel", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(UUID.fromString(orderId));
                        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
                    });
        }

        @Test
        @DisplayName("when non-admin cancels order returns 403")
        void whenNotAdmin_returns403() {
            webTestClient
                    .post()
                    .uri("/orders/{id}/cancel", ORDER_PENDING_A)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("when order is already cancelled returns 409")
        void whenAlreadyCancelled_returns409() {
            webTestClient
                    .post()
                    .uri("/orders/{id}/cancel", ORDER_CANCELLED_B)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("DELETE /orders/{id}")
    class DeleteOrder {

        @Test
        @DisplayName("when admin deletes order returns 204")
        void whenAdmin_returns204() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .delete()
                    .uri("/orders/{id}", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isNoContent();

            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .get()
                    .uri("/orders/{id}", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
