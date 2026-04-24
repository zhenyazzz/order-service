package com.innowise.orderservice.consumer;

public record PaymentCreatedEvent(
    String paymentId,
    String orderId,
    PaymentStatus status
) {}
