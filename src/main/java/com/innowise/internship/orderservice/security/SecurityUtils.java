package com.innowise.internship.orderservice.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.innowise.internship.orderservice.exception.security.SecurityContextException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityUtils {

    private static final String USER_NOT_FOUND_MESSAGE = "User not found in security context";

    public Optional<CurrentUser> getCurrentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(CurrentUser.class::isInstance)
                .map(CurrentUser.class::cast);
    }

    public UUID getCurrentUserId() {
        return getCurrentUser()
                .map(CurrentUser::userId)
                .orElseThrow(() -> new SecurityContextException(USER_NOT_FOUND_MESSAGE));
    }

    public String getCurrentUserEmail() {
        return getCurrentUser()
                .map(CurrentUser::email)
                .orElseThrow(() -> new SecurityContextException(USER_NOT_FOUND_MESSAGE));
    }

    public List<String> getCurrentUserRoles() {
        return getCurrentUser()
                .map(user -> user.roles().stream()
                    .map(Roles::getAuthority)
                    .toList())
                .orElseThrow(() -> new SecurityContextException(USER_NOT_FOUND_MESSAGE));
    }

    public boolean isAdmin() {
        return getCurrentUser()
            .map(u -> u.roles().contains(Roles.ADMIN))
            .orElse(false);
    }
}
