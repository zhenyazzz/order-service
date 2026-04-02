package com.innowise.internship.orderservice.exception.notfound;

public class ItemNotFoundException extends ResourceNotFoundException {

    public ItemNotFoundException(String message) {
        super(message);
    }
}
