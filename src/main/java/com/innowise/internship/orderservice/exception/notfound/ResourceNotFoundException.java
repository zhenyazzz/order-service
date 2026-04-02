package com.innowise.internship.orderservice.exception.notfound;

public abstract class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
