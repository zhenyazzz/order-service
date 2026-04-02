package com.innowise.internship.orderservice.exception;

public class ItemNotFoundException extends ResourceNotFoundException {

    public ItemNotFoundException(String message) {
        super(message);
    }
}
