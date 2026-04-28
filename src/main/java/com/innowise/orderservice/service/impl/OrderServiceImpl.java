package com.innowise.orderservice.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderStatusRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.service.OrderService;

import com.innowise.orderservice.client.UserIntegrationService;
import com.innowise.orderservice.consumer.PaymentCreatedEvent;
import com.innowise.orderservice.consumer.PaymentStatus;
import com.innowise.orderservice.exception.conflict.InvalidOrderStateException;
import com.innowise.orderservice.exception.security.ForbiddenException;
import com.innowise.orderservice.mapper.OrderCommandMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.mapper.ProcessedPaymentEventMapper;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.enums.OrderStatus;
import com.innowise.orderservice.persistence.OrderPersistence;
import com.innowise.orderservice.repository.ProcessedPaymentEventRepository;
import com.innowise.orderservice.repository.specification.OrderSpecification;
import com.innowise.orderservice.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderPersistence orderPersistence;
    private final UserIntegrationService userIntegrationService;
    private final OrderMapper orderMapper;
    private final OrderCommandMapper orderCommandMapper;
    private final ProcessedPaymentEventRepository processedPaymentRepository;
    private final ProcessedPaymentEventMapper processedPaymentEventMapper;

    @Override
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        UserResponse user = userIntegrationService.getInternalUserById(userId);
        Order order = orderPersistence.saveNewOrder(orderCommandMapper.toCreateCommand(user.id(), request));
        return orderMapper.toResponse(order, user);
    }

    @Override
    public OrderResponse getOrderById(UUID orderId, UUID currentUserId) {
        Order order = orderPersistence.findById(orderId);

        validateAccess(order, currentUserId);

        return toResponseWithUser(order);
    }

    @Override
    public Page<OrderResponse> getOrders(
            OrderSearchFilterRequest filter,
            Pageable pageable,
            UUID currentUserId
    ) {
        boolean isAdmin = SecurityUtils.isAdmin();

        UUID userFilter = isAdmin ? null : currentUserId;

        return findOrdersWithUserEnrichment(filter, pageable, userFilter);
    }

    @Override
    public Page<OrderResponse> getOrdersByUserId(
            OrderSearchFilterRequest filter,
            Pageable pageable,
            UUID userId
    ) {
        return findOrdersWithUserEnrichment(filter, pageable, userId);
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(
            UUID orderId,
            UUID currentUserId,
            UpdateOrderRequest request
    ) {
        Order order = orderPersistence.findById(orderId);

        validateAccess(order, currentUserId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Only PENDING orders can be updated; current status: " + order.getStatus());
        }

        Order updated = orderPersistence.updateOrder(order, orderCommandMapper.toUpdateItemsCommand(request));

        return toResponseWithUser(updated);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(
            UUID orderId,
            UpdateOrderStatusRequest request,
            UUID currentUserId
    ) {
        Order order = orderPersistence.findById(orderId);

        validateAccess(order, currentUserId);

        OrderStatus current = order.getStatus();
        OrderStatus target = request.status();

        if (current == target) {
            return toResponseWithUser(order);
        }

        if (!current.canTransitionTo(target)) {
            throw new InvalidOrderStateException(
                "Cannot change status from " + current + " to " + target
            );
        }

        Order updated = orderPersistence.updateStatus(order, target);

        return toResponseWithUser(updated);
    }

    @Override
    public void deleteOrder(UUID orderId) {
        orderPersistence.deleteById(orderId);
    }

    @Override
    public void deleteOrdersForUser(UUID userId) {
        orderPersistence.deleteAllByUserId(userId);
    }

    private void validateAccess(Order order, UUID currentUserId) {
        boolean isOwner = order.getUserId().equals(currentUserId);
        boolean isAdmin = SecurityUtils.isAdmin();

        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("No access to this order");
        }
    }

    @Override
    @Transactional
    public void processPaymentEvent(PaymentCreatedEvent paymentCreatedEvent) {
        String paymentId = paymentCreatedEvent.paymentId();

        if (processedPaymentRepository.existsById(paymentId)) {
            log.debug(
                "Skipping already processed payment event: paymentId={}, orderId={}, status={}",
                paymentId,
                paymentCreatedEvent.orderId(),
                paymentCreatedEvent.status()
            );
            return;
        }

        try {
            processedPaymentRepository.saveAndFlush(processedPaymentEventMapper.toEntity(paymentCreatedEvent));
        } catch (DataIntegrityViolationException duplicatePayment) {
            log.debug(
                "Skipping duplicate payment event: paymentId={}, orderId={}, status={}",
                paymentId,
                paymentCreatedEvent.orderId(),
                paymentCreatedEvent.status()
            );
            return;
        }

        if (paymentCreatedEvent.status() == PaymentStatus.FAILED) {
            log.debug(
                "Ignoring failed payment attempt for order state update: paymentId={}, orderId={}",
                paymentId,
                paymentCreatedEvent.orderId()
            );
            return;
        }

        Order order = orderPersistence.findById(UUID.fromString(paymentCreatedEvent.orderId()));
        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CONFIRMED) {
            return;
        }

        if (currentStatus != OrderStatus.PENDING) {
            log.warn(
                "Skipping successful payment event for non-pending order: paymentId={}, orderId={}, currentStatus={}",
                paymentCreatedEvent.paymentId(),
                paymentCreatedEvent.orderId(),
                currentStatus
            );
            return;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderPersistence.save(order);
    }

    private OrderResponse toResponseWithUser(Order order) {
        UserResponse user = userIntegrationService.getInternalUserById(order.getUserId());
        return orderMapper.toResponse(order, user);
    }

    private Page<OrderResponse> findOrdersWithUserEnrichment(
            OrderSearchFilterRequest filter,
            Pageable pageable,
            UUID userId
    ) {
        Specification<Order> spec = OrderSpecification.byCriteria(orderCommandMapper.toSearchCriteria(filter));

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
