package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = OrderItemMapper.class)
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "orderItems", source = "orderItems")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", expression = "java(OrderStatus.PENDING)")
    Order toEntity(UUID userId, List<OrderItem> orderItems);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "totalPrice", source = "order.totalPrice")
    @Mapping(target = "orderItems", source = "order.orderItems")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "user", source = "user")
    OrderResponse toResponse(Order order, UserResponse user);

    default OrderResponse toResponse(Order order) {
        return toResponse(order, null);
    }

    @AfterMapping
    default void fillCalculatedFields(@MappingTarget Order order) {
        BigDecimal total = BigDecimal.ZERO;
        if (order.getOrderItems() != null) {
            for (OrderItem orderItem : order.getOrderItems()) {
                orderItem.setOrder(order);
                if (orderItem.getItem() != null && orderItem.getItem().getPrice() != null) {
                    BigDecimal lineTotal = orderItem.getItem().getPrice()
                            .multiply(BigDecimal.valueOf(orderItem.getQuantity()));
                    total = total.add(lineTotal);
                }
            }
        }
        order.setTotalPrice(total);
    }
}
