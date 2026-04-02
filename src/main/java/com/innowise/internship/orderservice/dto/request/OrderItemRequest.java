package com.innowise.internship.orderservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull(message = "Item id is required")
        UUID itemId,
        @Positive(message = "Quantity must be greater than zero")
        int quantity
) {
}
