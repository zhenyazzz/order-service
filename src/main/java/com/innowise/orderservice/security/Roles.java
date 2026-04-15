package com.innowise.orderservice.security;

public enum Roles {

    ADMIN("ROLE_ADMIN"),
    USER("ROLE_USER");

    private final String authority;

    Roles(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return authority;
    }

    public boolean matches(String roleFromContext) {
        return authority.equals(roleFromContext);
    }
}

