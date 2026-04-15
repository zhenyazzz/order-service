package com.innowise.orderservice.utils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.experimental.UtilityClass;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.ItemResponse;
import com.innowise.orderservice.dto.response.OrderItemResponse;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.enums.OrderStatus;

@UtilityClass
public class OrderTestDataFactory {

    public static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    public static final UUID OTHER_USER_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-ccccccccccc1");
    public static final UUID ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
    public static final UUID ITEM_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    public static final String USER_EMAIL = "buyer@example.com";

    public UserResponse buildUserResponse() {
        return buildUserResponse(USER_ID, USER_EMAIL);
    }

    public UserResponse buildUserResponse(UUID id, String email) {
        return new UserResponse(id, "Test", "User", null, email);
    }

    public UserResponse userWithoutId(String email) {
        return new UserResponse(null, "X", "Y", null, email);
    }

    public CreateOrderRequest buildCreateOrderRequest() {
        return new CreateOrderRequest(
                List.of(new OrderItemRequest(ITEM_ID, 2))
        );
    }

    public OrderSearchFilterRequest emptyFilter() {
        return new OrderSearchFilterRequest(null, null, null);
    }

    public Order buildOrder(OrderStatus status) {
        return buildOrder(ORDER_ID, USER_ID, status);
    }

    public Order buildOrder(UUID id, UUID userId, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("20.00"));
        order.setCreatedAt(Instant.parse("2025-06-01T12:00:00Z"));
        order.setUpdatedAt(Instant.parse("2025-06-01T12:00:00Z"));
        order.setVersion(0L);
        return order;
    }

    public OrderResponse buildOrderResponse(Order order) {
        return buildOrderResponse(order, null);
    }

    public OrderResponse buildOrderResponse(Order order, UserResponse user) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalPrice(),
                List.of(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                user
        );
    }

    public OrderResponse buildOrderResponse() {
        Order o = buildOrder(OrderStatus.PENDING);
        ItemResponse item = new ItemResponse(ITEM_ID, "Demo", new BigDecimal("10.00"));
        OrderItemResponse line = new OrderItemResponse(UUID.randomUUID(), item, 2);
        UserResponse user = buildUserResponse();
        return new OrderResponse(
                o.getId(),
                o.getStatus(),
                o.getTotalPrice(),
                List.of(line),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                user
        );
    }

    public UpdateOrderRequest buildUpdateOrderRequest() {
        return new UpdateOrderRequest(
                List.of(new OrderItemRequest(ITEM_ID, 3))
        );
    }
}
