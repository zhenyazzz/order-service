package com.innowise.internship.orderservice.repository;

import com.innowise.internship.orderservice.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
}
