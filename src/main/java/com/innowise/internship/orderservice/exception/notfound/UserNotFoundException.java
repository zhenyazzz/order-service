package com.innowise.internship.orderservice.exception.notfound;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
