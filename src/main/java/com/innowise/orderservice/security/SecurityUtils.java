package com.innowise.orderservice.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.innowise.orderservice.exception.security.SecurityContextException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityUtils {

    private static final String USER_NOT_FOUND_MESSAGE = "User not found in security context";

    public Optional<CurrentUser> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Object principal = auth.getPrincipal();

        if (!(principal instanceof CurrentUser user)) {
            return Optional.empty();
        }

        return Optional.of(user);
    }

    private CurrentUser requireUser() {
        return getCurrentUser()
                .orElseThrow(() -> new SecurityContextException(USER_NOT_FOUND_MESSAGE));
    }

    public UUID getCurrentUserId() {
        return requireUser().userId();
    }

    public String getCurrentUserEmail() {
        return requireUser().email();
    }

    public List<String> getCurrentUserRoles() {
        return requireUser().roles().stream()
                .map(Roles::getAuthority)
                .toList();
    }

    public boolean isAdmin() {
        return requireUser().roles().contains(Roles.ADMIN);
    }
}
