package com.innowise.orderservice.application.order;

import java.util.List;

public record UpdateOrderItemsCommand(List<OrderItemCommand> orderItems) {
}
