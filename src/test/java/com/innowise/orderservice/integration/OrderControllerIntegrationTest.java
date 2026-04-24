package com.innowise.orderservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderStatusRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.model.enums.OrderStatus;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.utils.OrderTestDataFactory;

@DisplayName("Order API integration tests (Controller → Service → Repository → DB)")
class OrderControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
    private static final UUID ORDER_PENDING_A = UUID.fromString("aaaaaaaa-0001-4001-8001-000000000001");
    private static final UUID ORDER_CONFIRMED_A = UUID.fromString("aaaaaaaa-0002-4002-8002-000000000002");
    private static final UUID ORDER_CANCELLED_B = UUID.fromString("aaaaaaaa-0003-4003-8003-000000000003");
    private static final UUID USER_C_NO_SEED = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc1");
    private static final UUID ITEM_DEMO_GADGET = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void resetSeedOrdersState() {
        orderRepository.findById(ORDER_PENDING_A).ifPresent(o -> {
            o.setStatus(OrderStatus.PENDING);
            orderRepository.save(o);
        });

        orderRepository.findById(ORDER_CONFIRMED_A).ifPresent(o -> {
            o.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(o);
        });

        orderRepository.findById(ORDER_CANCELLED_B).ifPresent(o -> {
            o.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(o);
        });
    }

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
    @DisplayName("GET /orders/{id}")
    class GetOrderById {

        @Test
        @DisplayName("when order belongs to current user returns 200")
        void whenOwner_returns200WithUserData() {
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .get()
                    .uri("/orders/{id}", ORDER_PENDING_A)
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
        @DisplayName("when order belongs to another user returns 403")
        void whenOtherUsersOrder_returns403() {
            webTestClient
                    .get()
                    .uri("/orders/{id}", ORDER_PENDING_A)
                    .header("X-User-Id", USER_B.toString())
                    .header("X-User-Email", "other@example.com")
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("when owner reads non-PENDING order returns 200")
        void whenConfirmedOrder_returns200() {
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .get()
                    .uri("/orders/{id}", ORDER_CONFIRMED_A)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(ORDER_CONFIRMED_A);
                        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
                    });
        }

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
        @DisplayName("when admin acts as another principal still reads another user's order (200)")
        void whenAdminWithDifferentUserId_readsOtherUsersOrder_returns200() {
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .get()
                    .uri("/orders/{id}", ORDER_PENDING_A)
                    .header("X-User-Id", USER_B.toString())
                    .header("X-User-Email", "other@example.com")
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
        @DisplayName("when user requests orders returns page of own orders only")
        void whenUser_returnsOwnOrders() {
            stubInternalUsersByIds("[" + userResponseJson(USER_A, "Test", OrderTestDataFactory.USER_EMAIL) + "]");

            webTestClient
                    .get()
                    .uri("/orders?page=0&size=20")
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

        @Test
        @DisplayName("when admin filters by createdFrom and createdTo returns orders in range only")
        void whenAdmin_filtersByCreationDateRange_returnsMatchingPage() {
            stubSeedUsersBatch();

            webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("page", 0)
                            .queryParam("size", 20)
                            .queryParam("createdFrom", "2025-03-11T00:00:00Z")
                            .queryParam("createdTo", "2025-03-20T23:59:59Z")
                            .build())
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalElements").isEqualTo(2)
                    .jsonPath("$.content.length()").isEqualTo(2);
        }

        @Test
        @DisplayName("when admin filters by statuses returns only matching statuses")
        void whenAdmin_filtersByStatuses_returnsMatchingPage() {
            stubSeedUsersBatch();

            webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("page", 0)
                            .queryParam("size", 20)
                            .queryParam("statuses", "CANCELLED")
                            .build())
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalElements").isEqualTo(1)
                    .jsonPath("$.content[0].status").isEqualTo("CANCELLED")
                    .jsonPath("$.content[0].id").isEqualTo(ORDER_CANCELLED_B.toString());
        }

        @Test
        @DisplayName("when admin combines date range and statuses filters apply both")
        void whenAdmin_filtersByDateRangeAndStatuses_returnsMatchingPage() {
            stubSeedUsersBatch();

            webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("page", 0)
                            .queryParam("size", 20)
                            .queryParam("createdFrom", "2025-03-14T00:00:00Z")
                            .queryParam("createdTo", "2025-03-16T23:59:59Z")
                            .queryParam("statuses", "PENDING")
                            .build())
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalElements").isEqualTo(1)
                    .jsonPath("$.content[0].id").isEqualTo(ORDER_PENDING_A.toString());
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
    @DisplayName("PUT /orders/{id}")
    class UpdateOrder {

        @Test
        @DisplayName("when PENDING order is updated returns 200")
        void whenPending_returns200() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            UpdateOrderRequest update = new UpdateOrderRequest(
                    List.of(new OrderItemRequest(ITEM_DEMO_GADGET, 3))
            );

            webTestClient
                    .put()
                    .uri("/orders/{id}", orderId)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(update)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(UUID.fromString(orderId));
                        assertThat(response.totalPrice()).isEqualByComparingTo("76.50");
                    });
        }

        @Test
        @DisplayName("when non-owner updates another user's PENDING order returns 403")
        void whenNonOwner_returns403() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            UpdateOrderRequest update = new UpdateOrderRequest(
                    List.of(new OrderItemRequest(ITEM_DEMO_GADGET, 3))
            );

            webTestClient
                    .put()
                    .uri("/orders/{id}", orderId)
                    .header("X-User-Id", USER_B.toString())
                    .header("X-User-Email", "other@example.com")
                    .header("X-User-Roles", "ROLE_USER")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(update)
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("when admin updates another user's PENDING order returns 200")
        void whenAdmin_updatesAnotherUsersOrder_returns200() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            UpdateOrderRequest update = new UpdateOrderRequest(
                    List.of(new OrderItemRequest(ITEM_DEMO_GADGET, 1))
            );

            webTestClient
                    .put()
                    .uri("/orders/{id}", orderId)
                    .header("X-User-Id", USER_B.toString())
                    .header("X-User-Email", "other@example.com")
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(update)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(UUID.fromString(orderId));
                        assertThat(response.user().id()).isEqualTo(USER_A);
                    });
        }
    }

    @Nested
    @DisplayName("PATCH /orders/{id}/status")
    class UpdateOrderStatus {

        @Test
        @DisplayName("when owner cancels PENDING order returns 200")
        void whenOwnerCancelsPending_returns200() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .patch()
                    .uri("/orders/{id}/status", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED))
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
        @DisplayName("when cancel requested again on CANCELLED order returns 200 (idempotent)")
        void whenAlreadyCancelled_patchSameStatus_returns200() {
            String orderId = createOrderId(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);
            stubInternalUserById(USER_A, "Test", OrderTestDataFactory.USER_EMAIL);

            webTestClient
                    .patch()
                    .uri("/orders/{id}/status", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED))
                    .exchange()
                    .expectStatus().isOk();

            webTestClient
                    .patch()
                    .uri("/orders/{id}/status", UUID.fromString(orderId))
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED))
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
        @DisplayName("when admin sets CANCELLED on already cancelled order returns 200")
        void whenAdminPatchesAlreadyCancelled_returns200() {
            stubInternalUserById(USER_B, "Other", "other@example.com");

            webTestClient
                    .patch()
                    .uri("/orders/{id}/status", ORDER_CANCELLED_B)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.id()).isEqualTo(ORDER_CANCELLED_B);
                        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
                    });
        }

        @Test
        @DisplayName("when non-owner user patches another user's order returns 403")
        void whenNonOwner_returns403() {
            webTestClient
                    .patch()
                    .uri("/orders/{id}/status", ORDER_PENDING_A)
                    .header("X-User-Id", USER_B.toString())
                    .header("X-User-Email", "other@example.com")
                    .header("X-User-Roles", "ROLE_USER")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED))
                    .exchange()
                    .expectStatus().isForbidden();
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
                    .header("Idempotency-Key", UUID.randomUUID().toString())
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

    @Nested
    @DisplayName("DELETE /orders/user/{userId}")
    class DeleteOrdersByUserId {

        @Test
        @DisplayName("when admin deletes all orders for user returns 204 and list is empty")
        void whenAdmin_deletesAllForUser_returns204_andListEmpty() {
            createOrder(USER_C_NO_SEED, "NoSeed", "noseed@example.com", UUID.randomUUID().toString());
            createOrder(USER_C_NO_SEED, "NoSeed", "noseed@example.com", UUID.randomUUID().toString());

            stubInternalUsersByIds("[" + userResponseJson(USER_C_NO_SEED, "NoSeed", "noseed@example.com") + "]");

            webTestClient
                    .get()
                    .uri("/orders/user/{userId}?page=0&size=20", USER_C_NO_SEED)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalElements").isEqualTo(2);

            webTestClient
                    .delete()
                    .uri("/orders/user/{userId}", USER_C_NO_SEED)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .exchange()
                    .expectStatus().isNoContent();

            stubInternalUsersByIds("[" + userResponseJson(USER_C_NO_SEED, "NoSeed", "noseed@example.com") + "]");

            webTestClient
                    .get()
                    .uri("/orders/user/{userId}?page=0&size=20", USER_C_NO_SEED)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalElements").isEqualTo(0)
                    .jsonPath("$.content.length()").isEqualTo(0);
        }

        @Test
        @DisplayName("when non-admin deletes by user returns 403")
        void whenNotAdmin_returns403() {
            webTestClient
                    .delete()
                    .uri("/orders/user/{userId}", USER_B)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_USER")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("when Idempotency-Key is missing returns 400")
        void whenIdempotencyKeyMissing_returns400() {
            webTestClient
                    .delete()
                    .uri("/orders/user/{userId}", USER_B)
                    .header("X-User-Id", USER_A.toString())
                    .header("X-User-Email", OrderTestDataFactory.USER_EMAIL)
                    .header("X-User-Roles", "ROLE_ADMIN")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }
}
