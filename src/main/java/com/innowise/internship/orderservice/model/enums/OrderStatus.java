package com.innowise.internship.orderservice.model.enums;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean isCancelled() {
        return this == CANCELLED;
    }

}
    