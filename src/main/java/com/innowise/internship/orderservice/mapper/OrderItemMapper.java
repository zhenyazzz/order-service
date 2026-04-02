package com.innowise.internship.orderservice.mapper;

import com.innowise.internship.orderservice.dto.request.OrderItemRequest;
import com.innowise.internship.orderservice.dto.response.OrderItemResponse;
import com.innowise.internship.orderservice.model.Item;
import com.innowise.internship.orderservice.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ItemMapper.class)
public interface OrderItemMapper {

    OrderItemResponse toResponse(OrderItem orderItem);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "item", source = "item")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OrderItem toEntity(OrderItemRequest request, Item item);
}
