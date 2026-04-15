package com.innowise.orderservice.dto.request;

import com.innowise.orderservice.model.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
    @NotNull(message = "Status is required")
    OrderStatus status
) {

}
