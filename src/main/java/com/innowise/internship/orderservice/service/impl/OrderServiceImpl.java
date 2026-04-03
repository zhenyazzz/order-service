package com.innowise.internship.orderservice.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.innowise.internship.orderservice.dto.internal.UserResponse;
import com.innowise.internship.orderservice.dto.request.CreateOrderRequest;
import com.innowise.internship.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.internship.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.internship.orderservice.dto.response.OrderResponse;
import com.innowise.internship.orderservice.service.OrderPersistence;
import com.innowise.internship.orderservice.service.OrderService;

import com.innowise.internship.orderservice.client.UserIntegrationService;
import com.innowise.internship.orderservice.exception.security.SecurityContextException;
import com.innowise.internship.orderservice.mapper.OrderMapper;
import com.innowise.internship.orderservice.model.Order;
import com.innowise.internship.orderservice.repository.specification.OrderSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderPersistence orderPersistence;
    private final UserIntegrationService userIntegrationService;
    private final OrderMapper orderMapper;

    @Override
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        UserResponse userResponse = userIntegrationService.getInternalUserById(userId);
        return orderPersistence.saveNewOrder(userResponse, request);
    }

    @Override
    public OrderResponse getMyOrderById(UUID orderId, UUID currentUserId) {
        Order order = orderPersistence.findByIdAndUserId(orderId, currentUserId);
        return toResponseWithUser(order);
    }

    @Override
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderPersistence.findById(orderId);
        return toResponseWithUser(order);
    }

    @Override
    public Page<OrderResponse> getMyOrders(OrderSearchFilterRequest filter, Pageable pageable, UUID currentUserId) {
        return findOrdersWithUserEnrichment(filter, pageable, currentUserId);
    }

    @Override
    public Page<OrderResponse> getOrders(OrderSearchFilterRequest filter, Pageable pageable) {
        return findOrdersWithUserEnrichment(filter, pageable, null);
    }

    @Override
    public Page<OrderResponse> getOrdersByUserId(OrderSearchFilterRequest filter, Pageable pageable, UUID currentUserId) {
       return findOrdersWithUserEnrichment(filter, pageable, currentUserId);
    }

    @Override
    public OrderResponse cancelMyOrder(UUID orderId, UUID currentUserId) {
        Order order = orderPersistence.cancelByIdAndUserId(orderId, currentUserId);
        return toResponseWithUser(order);
    }

    @Override
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderPersistence.cancelById(orderId);
        return toResponseWithUser(order);
    }

    @Override
    public OrderResponse updateMyOrder(UUID orderId, UUID currentUserId, UpdateOrderRequest request) {
        if (currentUserId == null) {
            throw new SecurityContextException("Current user is not authenticated");
        }
        Order updatedOrder = orderPersistence.updateOrder(orderId, currentUserId, request);
        return toResponseWithUser(updatedOrder);
    }

    @Override
    public void deleteOrder(UUID orderId) {
        orderPersistence.deleteById(orderId);
    }

    private OrderResponse toResponseWithUser(Order order) {
        UserResponse user = userIntegrationService.getInternalUserById(order.getUserId());
        return orderMapper.toResponse(order, user);
    }

    private Page<OrderResponse> findOrdersWithUserEnrichment(OrderSearchFilterRequest filter, Pageable pageable, UUID userId) {
        Specification<Order> spec = OrderSpecification.byFilter(filter);
        if (userId != null) {
            spec = spec.and(OrderSpecification.hasUserId(userId));
        }
        Page<Order> orders = orderPersistence.findAll(spec, pageable);

        List<UUID> userIds = orders.getContent().stream()
                .map(Order::getUserId)
                .distinct()
                .toList();

        if (userIds.isEmpty()) {
            return orders.map(o -> orderMapper.toResponse(o, null));
        }

        List<UserResponse> users = userIntegrationService.getInternalUsersByIds(userIds);
        Map<UUID, UserResponse> userById = users.stream()
                .collect(Collectors.toMap(UserResponse::id, Function.identity()));

        return orders.map(o -> orderMapper.toResponse(o, userById.get(o.getUserId())));
    }
}
