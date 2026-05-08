package com.innowise.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.innowise.orderservice.model.ProcessedPaymentEvent;

@Repository
public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, String> {
}
