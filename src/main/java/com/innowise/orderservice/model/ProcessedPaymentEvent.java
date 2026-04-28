package com.innowise.orderservice.model;


import java.time.Instant;

import com.innowise.orderservice.consumer.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "processed_payment_events")
@EntityListeners(AuditingEntityListener.class)
public class ProcessedPaymentEvent {

    @Id
    @Column(name = "payment_id", nullable = false, updatable = false)
    private String paymentId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    @CreatedDate
    private Instant processedAt;

    @Column(name = "status", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

}
