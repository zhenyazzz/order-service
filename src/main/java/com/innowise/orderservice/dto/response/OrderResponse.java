package com.innowise.orderservice.dto.response;

import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.model.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemResponse> orderItems,
        Instant createdAt,
        Instant updatedAt,
        UserResponse user
) {
}
