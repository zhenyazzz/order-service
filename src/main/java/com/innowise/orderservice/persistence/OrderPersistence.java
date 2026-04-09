package com.innowise.orderservice.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.exception.conflict.InvalidOrderStateException;
import com.innowise.orderservice.exception.notfound.ItemNotFoundException;
import com.innowise.orderservice.exception.notfound.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.enums.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderPersistence {

    private static final String ORDER_NOT_FOUND_MESSAGE = "Order not found";

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE));
    }

    @Transactional(readOnly = true)
    public Order findByIdAndUserId(UUID id, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE));
        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> findAll(Specification<Order> spec, Pageable pageable) {
        return orderRepository.findAll(spec, pageable);
    }

    @Transactional
    public Order saveNewOrder(UserResponse userResponse, CreateOrderRequest request) {
        List<OrderItemRequest> orderItemRequests = request.orderItems();
        List<UUID> itemIds = orderItemRequests.stream()
                .map(OrderItemRequest::itemId)
                .distinct()
                .toList();
        Map<UUID, Item> itemsById = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<OrderItem> orderItems = orderItemRequests.stream()
                .map(orderItemRequest -> {
                    Item item = itemsById.get(orderItemRequest.itemId());
                    if (item == null) {
                        throw new ItemNotFoundException("Item not found: " + orderItemRequest.itemId());
                    }
                    return orderItemMapper.toEntity(orderItemRequest, item);
                })
                .toList();
        Order order = orderMapper.toEntity(userResponse.id(), orderItems);
        orderRepository.save(order);
        return order;
    }

    @Transactional
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    @Transactional
    public boolean updateStatus(UUID id, OrderStatus status, Long version) {
        int updated = orderRepository.updateStatus(id, status, version);
        return updated > 0;
    }

    @Transactional
    public void deleteById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE));
        orderRepository.delete(order);
    }

    @Transactional
    public Order updateOrder(Order order, UpdateOrderRequest request) {
        order.getOrderItems().clear();

        List<UUID> itemIds = request.orderItems().stream()
                .map(OrderItemRequest::itemId)
                .distinct()
                .toList();
        Map<UUID, Item> itemsById = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<OrderItem> newOrderItems = request.orderItems().stream()
                .map(orderItemRequest -> {
                    Item item = itemsById.get(orderItemRequest.itemId());
                    if (item == null) {
                        throw new ItemNotFoundException("Item not found: " + orderItemRequest.itemId());
                    }
                    OrderItem orderItem = orderItemMapper.toEntity(orderItemRequest, item);
                    order.addOrderItem(orderItem);
                    return orderItem;
                })
                .toList();

        if (newOrderItems.isEmpty()) {
            throw new InvalidOrderStateException("Order must contain at least one item");
        }

        BigDecimal newTotalPrice = newOrderItems.stream()
                .map(oi -> oi.getItem().getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalPrice(newTotalPrice);

        return orderRepository.save(order);
    }

}
