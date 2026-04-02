package com.innowise.internship.orderservice.exception.notfound;

public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}
