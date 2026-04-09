package com.innowise.orderservice.model.enums;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean isCancelled() {
        return this == CANCELLED;
    }

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }

}
    