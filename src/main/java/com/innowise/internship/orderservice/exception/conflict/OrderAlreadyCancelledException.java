package com.innowise.internship.orderservice.exception.conflict;

public class OrderAlreadyCancelledException extends RuntimeException {

    public OrderAlreadyCancelledException(String message) {
        super(message);
    }
}
