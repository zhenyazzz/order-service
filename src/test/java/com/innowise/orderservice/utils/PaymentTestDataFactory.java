package com.innowise.orderservice.utils;

import com.innowise.orderservice.consumer.PaymentCreatedEvent;
import com.innowise.orderservice.consumer.PaymentStatus;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PaymentTestDataFactory {

    public static final String PAYMENT_ID = "payment-test-1";

    public PaymentCreatedEvent buildPaymentCreatedEvent() {
        return buildPaymentCreatedEvent(PaymentStatus.SUCCESS);
    }

    public PaymentCreatedEvent buildPaymentCreatedEvent(PaymentStatus status) {
        return new PaymentCreatedEvent(
                PAYMENT_ID,
                OrderTestDataFactory.ORDER_ID.toString(),
                status
        );
    }
}
