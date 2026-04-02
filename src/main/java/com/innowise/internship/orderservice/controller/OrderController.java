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

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent(prefix = "create-order", ttlMinutes = 30)
    public OrderResponse createOrder(@RequestBody @Valid CreateOrderRequest request) {
        return orderService.createOrder(SecurityUtils.getCurrentUserId(), request);
    }

    @GetMapping("/me/{id}")
    public OrderResponse getMyOrderById(@PathVariable UUID id) {
        return orderService.getMyOrderById(id, SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse getOrderById(@PathVariable UUID id) {
        return orderService.getOrderById(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> getOrders(
            @Valid @ModelAttribute OrderSearchFilterRequest filter, 
             Pageable pageable) {
        return orderService.getOrders(filter, pageable);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> getOrdersByUserId(
            @PathVariable UUID userId, 
            @Valid @ModelAttribute OrderSearchFilterRequest filter, 
             Pageable pageable) {
        return orderService.getOrdersByUserId(filter, pageable, userId);
    }

    @GetMapping("/me")
    public Page<OrderResponse> getMyOrders(
            @Valid @ModelAttribute OrderSearchFilterRequest filter,
             Pageable pageable) {
        return orderService.getMyOrders(filter, pageable, SecurityUtils.getCurrentUserId());
    }

    @PutMapping("/me/{id}")
    public OrderResponse updateMyOrder(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateOrderRequest request) {
        return orderService.updateMyOrder(id, SecurityUtils.getCurrentUserId(), request);
    }

    @PostMapping("/me/{id}/cancel")
    public OrderResponse cancelMyOrder(@PathVariable UUID id) {
        return orderService.cancelMyOrder(id, SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return orderService.cancelOrder(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteOrder(@PathVariable UUID id) {
        orderService.deleteOrder(id);
    }

}
