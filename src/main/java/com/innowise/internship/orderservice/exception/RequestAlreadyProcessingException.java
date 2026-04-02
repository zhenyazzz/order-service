package com.innowise.internship.orderservice.exception;

public class RequestAlreadyProcessingException extends RuntimeException {

    public RequestAlreadyProcessingException(String message) {
        super(message);
    }

    public RequestAlreadyProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

