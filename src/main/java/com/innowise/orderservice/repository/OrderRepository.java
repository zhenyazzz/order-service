package com.innowise.orderservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.innowise.orderservice.model.Order;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @EntityGraph(value = "order-with-items", type = EntityGraph.EntityGraphType.LOAD)
    Optional<Order> findById(UUID id);

    @EntityGraph(value = "order-with-items", type = EntityGraph.EntityGraphType.LOAD)
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(value = "order-with-items", type = EntityGraph.EntityGraphType.LOAD)
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

}
