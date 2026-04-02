package com.innowise.internship.orderservice.service;

import com.innowise.internship.orderservice.dto.request.CreateOrderRequest;
import com.innowise.internship.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.internship.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.internship.orderservice.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(UUID userId, CreateOrderRequest request);

    OrderResponse getMyOrderById(UUID orderId, UUID currentUserId);

    OrderResponse getOrderById(UUID orderId);

    Page<OrderResponse> getMyOrders(OrderSearchFilterRequest filter, Pageable pageable, UUID currentUserId);

    Page<OrderResponse> getOrders(OrderSearchFilterRequest filter, Pageable pageable);

    Page<OrderResponse> getOrdersByUserId(OrderSearchFilterRequest filter, Pageable pageable, UUID currentUserId);

    OrderResponse cancelMyOrder(UUID orderId, UUID currentUserId);

    OrderResponse cancelOrder(UUID orderId);

    OrderResponse updateMyOrder(UUID orderId, UUID currentUserId, UpdateOrderRequest request);

    void deleteOrder(UUID orderId);
}
