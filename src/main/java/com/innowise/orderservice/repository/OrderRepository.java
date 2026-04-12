package com.innowise.orderservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.innowise.orderservice.model.Order;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @EntityGraph(value = "order-with-items", type = EntityGraph.EntityGraphType.LOAD)
    Optional<Order> findById(UUID id);

    @EntityGraph(value = "order-with-items", type = EntityGraph.EntityGraphType.LOAD)
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(value = "order-with-items", type = EntityGraph.EntityGraphType.LOAD)
    List<Order> findAllByUserId(UUID userId);

    @EntityGraph(value = "order-with-items", type = EntityGraph.EntityGraphType.LOAD)
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

}
