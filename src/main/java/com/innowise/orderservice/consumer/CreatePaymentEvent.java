package com.innowise.orderservice.consumer;

public record CreatePaymentEvent(
    String paymentId,
    String orderId,
    PaymentStatus status
) {}
