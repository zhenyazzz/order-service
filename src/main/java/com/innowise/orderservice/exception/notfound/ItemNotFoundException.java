package com.innowise.orderservice.exception.notfound;

public class ItemNotFoundException extends ResourceNotFoundException {

    public ItemNotFoundException(String message) {
        super(message);
    }
}
