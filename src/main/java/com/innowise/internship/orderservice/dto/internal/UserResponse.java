package com.innowise.internship.orderservice.dto.internal;

import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email
) {}
