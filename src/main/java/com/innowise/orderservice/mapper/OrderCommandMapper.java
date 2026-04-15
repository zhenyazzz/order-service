package com.innowise.orderservice.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.innowise.orderservice.application.order.CreateOrderCommand;
import com.innowise.orderservice.application.order.UpdateOrderItemsCommand;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.model.criteria.OrderSearchCriteria;

@Mapper
public interface OrderCommandMapper {

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "orderItems", source = "request.orderItems")
    CreateOrderCommand toCreateCommand(UUID userId, CreateOrderRequest request);

    UpdateOrderItemsCommand toUpdateItemsCommand(UpdateOrderRequest request);

    OrderSearchCriteria toSearchCriteria(OrderSearchFilterRequest request);
}
