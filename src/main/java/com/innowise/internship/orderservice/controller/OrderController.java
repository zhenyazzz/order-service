package com.innowise.internship.orderservice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innowise.internship.orderservice.dto.request.CreateOrderRequest;
import com.innowise.internship.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.internship.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.internship.orderservice.dto.response.OrderResponse;
import com.innowise.internship.orderservice.service.OrderService;

import jakarta.validation.Valid;

import com.innowise.internship.orderservice.security.SecurityUtils;
import com.innowise.internship.orderservice.aspect.Idempotent;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

/**
 * Order controller.
 * Handles order creation, retrieval, updates, cancellation, and deletion.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates a new order for the authenticated user.
     *
     * @param request order creation data
     * @return created order
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent(prefix = "create-order", ttlMinutes = 30)
    public OrderResponse createOrder(@RequestBody @Valid CreateOrderRequest request) {
        return orderService.createOrder(SecurityUtils.getCurrentUserId(), request);
    }

    /**
     * Returns the authenticated user's order by id.
     *
     * @param id order id
     * @return order details
     */
    @GetMapping("/me/{id}")
    public OrderResponse getMyOrderById(@PathVariable UUID id) {
        return orderService.getMyOrderById(id, SecurityUtils.getCurrentUserId());
    }

    /**
     * Returns an order by id (ADMIN only).
     *
     * @param id order id
     * @return order details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse getOrderById(@PathVariable UUID id) {
        return orderService.getOrderById(id);
    }

    /**
     * Returns all orders (ADMIN only).
     *
     * @param filter search filters
     * @param pageable pagination data
     * @return paged orders
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> getOrders(
            @Valid @ModelAttribute OrderSearchFilterRequest filter, 
             Pageable pageable) {
        return orderService.getOrders(filter, pageable);
    }

    /**
     * Returns orders for a specific user (ADMIN only).
     *
     * @param userId user id
     * @param filter search filters
     * @param pageable pagination data
     * @return paged orders for the user
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> getOrdersByUserId(
            @PathVariable UUID userId, 
            @Valid @ModelAttribute OrderSearchFilterRequest filter, 
             Pageable pageable) {
        return orderService.getOrdersByUserId(filter, pageable, userId);
    }

    /**
     * Returns the authenticated user's orders.
     *
     * @param filter search filters
     * @param pageable pagination data
     * @return paged orders of the current user
     */
    @GetMapping("/me")
    public Page<OrderResponse> getMyOrders(
            @Valid @ModelAttribute OrderSearchFilterRequest filter,
             Pageable pageable) {
        return orderService.getMyOrders(filter, pageable, SecurityUtils.getCurrentUserId());
    }

    /**
     * Updates the authenticated user's order.
     *
     * @param id order id
     * @param request update data
     * @return updated order
     */
    @PutMapping("/me/{id}")
    public OrderResponse updateMyOrder(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateOrderRequest request) {
        return orderService.updateMyOrder(id, SecurityUtils.getCurrentUserId(), request);
    }

    /**
     * Cancels the authenticated user's order.
     *
     * @param id order id
     * @return cancelled order
     */
    @PostMapping("/me/{id}/cancel")
    public OrderResponse cancelMyOrder(@PathVariable UUID id) {
        return orderService.cancelMyOrder(id, SecurityUtils.getCurrentUserId());
    }

    /**
     * Cancels an order (ADMIN only).
     *
     * @param id order id
     * @return cancelled order
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return orderService.cancelOrder(id);
    }

    /**
     * Deletes an order (ADMIN only).
     *
     * @param id order id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteOrder(@PathVariable UUID id) {
        orderService.deleteOrder(id);
    }

}
