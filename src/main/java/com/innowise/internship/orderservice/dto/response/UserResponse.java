package com.innowise.internship.orderservice.dto.response;

import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName
) {}