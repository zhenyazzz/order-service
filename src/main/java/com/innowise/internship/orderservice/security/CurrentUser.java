package com.innowise.internship.orderservice.security;

import java.util.List;
import java.util.UUID;

public record CurrentUser(
    UUID userId,
    String email,
    List<Roles> roles
) {}
