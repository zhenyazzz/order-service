package com.innowise.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateOrderRequest(
    @NotEmpty(message = "Order must contain at least one item")
    List<@Valid OrderItemRequest> orderItems
) {

}
