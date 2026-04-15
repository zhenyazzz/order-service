package com.innowise.orderservice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderStatusRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.service.OrderService;

import jakarta.validation.Valid;

import com.innowise.orderservice.security.SecurityUtils;
import com.innowise.orderservice.aspect.Idempotent;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

/**
 * REST API for orders under {@code /orders}.
 * Uses the authenticated user from the security context unless an endpoint is explicitly admin-only.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates an order for the current user.
     *
     * @return {@code 201 Created} with the new order body
     */
    @PostMapping
    @Idempotent(ttlMinutes = 30)
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody @Valid CreateOrderRequest request) {

        OrderResponse response = orderService.createOrder(
                SecurityUtils.getCurrentUserId(), request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns one order by id if the caller may access it (owner or admin).
     *
     * @param id order identifier
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                orderService.getOrderById(id, SecurityUtils.getCurrentUserId())
        );
    }

    /**
     * Paged list of orders for the current user, or all orders when the caller is an administrator.
     * Filter and sort are supplied as query parameters ({@link OrderSearchFilterRequest}, {@link Pageable}).
     */
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @Valid @ModelAttribute OrderSearchFilterRequest filter,
            Pageable pageable) {

        return ResponseEntity.ok(
                orderService.getOrders(filter, pageable, SecurityUtils.getCurrentUserId())
        );
    }

    /**
     * Paged orders belonging to a specific user. Restricted to administrators.
     *
     * @param userId owner whose orders are listed
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<OrderResponse>> getOrdersByUserId(
            @PathVariable UUID userId,
            @Valid @ModelAttribute OrderSearchFilterRequest filter,
            Pageable pageable) {

        return ResponseEntity.ok(
                orderService.getOrdersByUserId(filter, pageable, userId)
        );
    }

    /**
     * Deletes all orders for a given user. Restricted to administrators.
     *
     * @param userId owner whose orders are removed
     */
    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Idempotent()
    public ResponseEntity<Void> deleteOrdersByUserId(@PathVariable UUID userId) {
        orderService.deleteOrdersForUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Full update of order content (e.g. line items) for an existing order.
     *
     * @param id order identifier
     */
    @PutMapping("/{id}")
    @Idempotent(ttlMinutes = 60)
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateOrderRequest request) {

        return ResponseEntity.ok(
                orderService.updateOrder(id, SecurityUtils.getCurrentUserId(), request)
        );
    }

    /**
     * Partial update: changes order status when the transition is allowed.
     *
     * @param id order identifier
     */
    @PatchMapping("/{id}/status")
    @Idempotent(ttlMinutes = 60)
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateOrderStatusRequest request) {

        return ResponseEntity.ok(
                orderService.updateOrderStatus(
                        id, request, SecurityUtils.getCurrentUserId()
                )
        );
    }

    /**
     * Deletes an order by id. Restricted to administrators.
     *
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Idempotent()
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
