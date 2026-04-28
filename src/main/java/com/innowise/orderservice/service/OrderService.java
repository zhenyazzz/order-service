package com.innowise.orderservice.service;

import com.innowise.orderservice.consumer.PaymentCreatedEvent;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderStatusRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Application API for order lifecycle: create, read, update status, search, and delete.
 * Implementations enforce access (owner or administrator) and enrich responses with user data.
 */
public interface OrderService {

    /**
     * Persists a new order for the given user.
     *
     * @param userId  owner of the order
     * @param request line items and payload from the client
     * @return created order with enriched user information
     */
    OrderResponse createOrder(UUID userId, CreateOrderRequest request);

    /**
     * Returns a single order if the caller is the owner or an administrator.
     *
     * @param orderId        order identifier
     * @param currentUserId  authenticated user performing the request
     */
    OrderResponse getOrderById(UUID orderId, UUID currentUserId);

    /**
     * Page of orders matching the filter. Non-admin callers only see their own orders;
     * administrators see all orders (filter is not scoped by user id here).
     *
     * @param filter         optional search criteria
     * @param pageable       paging and sorting
     * @param currentUserId  caller used for access scoping when not admin
     */
    Page<OrderResponse> getOrders(
            OrderSearchFilterRequest filter,
            Pageable pageable,
            UUID currentUserId
    );

    /**
     * Page of orders for a specific user, typically used in administrative flows.
     *
     * @param filter   optional search criteria (applied within that user's orders)
     * @param pageable paging and sorting
     * @param userId   user whose orders are listed
     */
    Page<OrderResponse> getOrdersByUserId(
            OrderSearchFilterRequest filter,
            Pageable pageable,
            UUID userId
    );

    /**
     * Updates order content (e.g. line items) for an existing order.
     * Caller must be the order owner or an administrator.
     *
     * @param orderId        order to update
     * @param currentUserId  authenticated user
     * @param request        new payload
     */
    OrderResponse updateOrder(
            UUID orderId,
            UUID currentUserId,
            UpdateOrderRequest request
    );

    /**
     * Changes order status when the transition is allowed by the domain rules.
     * Caller must be the order owner or an administrator.
     *
     * @param orderId        order to update
     * @param request        target status
     * @param currentUserId  authenticated user
     */
    OrderResponse updateOrderStatus(
            UUID orderId,
            UpdateOrderStatusRequest request,
            UUID currentUserId
    );

    /**
     * Removes the order with the given identifier (semantics depend on persistence, e.g. soft delete).
     *
     * @param orderId order to remove
     */
    void deleteOrder(UUID orderId);

    /**
     * Removes all orders owned by the given user. Intended for administrative flows (e.g. account deletion).
     *
     * @param userId user whose orders are removed
     */
    void deleteOrdersForUser(UUID userId);
    
    /**
     * Processes a payment event.
     *
     * @param paymentCreatedEvent payment event
     */
    void processPaymentEvent(PaymentCreatedEvent paymentCreatedEvent);
}
