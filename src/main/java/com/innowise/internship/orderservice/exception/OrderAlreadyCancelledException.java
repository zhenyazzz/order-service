package com.innowise.internship.orderservice.exception;

public class OrderAlreadyCancelledException extends RuntimeException {

    public OrderAlreadyCancelledException(String message) {
        super(message);
    }
}
