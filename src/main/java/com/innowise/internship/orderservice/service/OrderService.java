package com.innowise.internship.orderservice.service;

import com.innowise.internship.orderservice.dto.request.CreateOrderRequest;
import com.innowise.internship.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.internship.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.internship.orderservice.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Order use cases: create, read, update, cancel, and delete orders.
 */
public interface OrderService {

    /**
     * Creates a new order for the given user.
     *
     * @param userId user id
     * @param request order items
     * @return created order
     */
    OrderResponse createOrder(UUID userId, CreateOrderRequest request);

    /**
     * Returns the user's order when it belongs to the current user and is modifiable.
     *
     * @param orderId order id
     * @param currentUserId current user id
     * @return order details
     */
    OrderResponse getMyOrderById(UUID orderId, UUID currentUserId);

    /**
     * Returns an order by id.
     *
     * @param orderId order id
     * @return order details
     */
    OrderResponse getOrderById(UUID orderId);

    /**
     * Returns the current user's orders.
     *
     * @param filter search filters
     * @param pageable pagination data
     * @param currentUserId current user id
     * @return paged orders
     */
    Page<OrderResponse> getMyOrders(OrderSearchFilterRequest filter, Pageable pageable, UUID currentUserId);

    /**
     * Returns all orders.
     *
     * @param filter search filters
     * @param pageable pagination data
     * @return paged orders
     */
    Page<OrderResponse> getOrders(OrderSearchFilterRequest filter, Pageable pageable);

    /**
     * Returns orders for a specific user.
     *
     * @param filter search filters
     * @param pageable pagination data
     * @param currentUserId target user id
     * @return paged orders
     */
    Page<OrderResponse> getOrdersByUserId(OrderSearchFilterRequest filter, Pageable pageable, UUID currentUserId);

    /**
     * Cancels the current user's order.
     *
     * @param orderId order id
     * @param currentUserId current user id
     * @return cancelled order
     */
    OrderResponse cancelMyOrder(UUID orderId, UUID currentUserId);

    /**
     * Cancels an order.
     *
     * @param orderId order id
     * @return cancelled order
     */
    OrderResponse cancelOrder(UUID orderId);

    /**
     * Updates the current user's order.
     *
     * @param orderId order id
     * @param currentUserId current user id
     * @param request update data
     * @return updated order
     */
    OrderResponse updateMyOrder(UUID orderId, UUID currentUserId, UpdateOrderRequest request);

    /**
     * Deletes an order.
     *
     * @param orderId order id
     */
    void deleteOrder(UUID orderId);
}
