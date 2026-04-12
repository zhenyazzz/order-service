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

import com.innowise.orderservice.application.order.CreateOrderCommand;
import com.innowise.orderservice.application.order.OrderItemCommand;
import com.innowise.orderservice.application.order.UpdateOrderItemsCommand;
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
        return orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE));
   
    }

    @Transactional(readOnly = true)
    public Page<Order> findAll(Specification<Order> spec, Pageable pageable) {
        return orderRepository.findAll(spec, pageable);
    }

    @Transactional
    public Order saveNewOrder(CreateOrderCommand command) {
        List<OrderItemCommand> items = command.orderItems();
        List<UUID> itemIds = items.stream()
                .map(OrderItemCommand::itemId)
                .distinct()
                .toList();
        Map<UUID, Item> itemsById = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<OrderItem> orderItems = items.stream()
                .map(line -> {
                    Item item = itemsById.get(line.itemId());
                    if (item == null) {
                        throw new ItemNotFoundException("Item not found: " + line.itemId());
                    }
                    return orderItemMapper.toEntity(line, item);
                })
                .toList();
        Order order = orderMapper.toEntity(command.userId(), orderItems);
        orderRepository.save(order);
        return order;
    }

    @Transactional
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    @Transactional
    public Order updateStatus(Order order, OrderStatus status) {
        order.setStatus(status);
        return orderRepository.save(order);
    }

    @Transactional
    public void deleteById(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE);
        }
        orderRepository.deleteById(orderId);
    }

    @Transactional
    public Order updateOrder(Order order, UpdateOrderItemsCommand command) {
        order.getOrderItems().clear();
        orderRepository.flush();

        List<OrderItemCommand> items = command.orderItems();
        List<UUID> itemIds = items.stream()
                .map(OrderItemCommand::itemId)
                .distinct()
                .toList();
        Map<UUID, Item> itemsById = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<OrderItem> newOrderItems = items.stream()
                .map(line -> {
                    Item item = itemsById.get(line.itemId());
                    if (item == null) {
                        throw new ItemNotFoundException("Item not found: " + line.itemId());
                    }
                    OrderItem orderItem = orderItemMapper.toEntity(line, item);
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
