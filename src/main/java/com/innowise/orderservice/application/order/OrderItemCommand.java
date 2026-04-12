package com.innowise.orderservice.application.order;

import java.util.UUID;

public record OrderItemCommand(UUID itemId, int quantity) {
}
