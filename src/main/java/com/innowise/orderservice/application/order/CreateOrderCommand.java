package com.innowise.orderservice.application.order;

import java.util.List;
import java.util.UUID;


public record CreateOrderCommand(UUID userId, List<OrderItemCommand> orderItems) {
}
