package com.innowise.internship.orderservice.dto.response;

import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        ItemResponse item,
        int quantity
) {
}
