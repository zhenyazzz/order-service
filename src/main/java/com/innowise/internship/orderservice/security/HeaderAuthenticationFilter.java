package com.innowise.internship.orderservice.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.innowise.internship.orderservice.dto.error.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");
        String emailHeader = request.getHeader("X-User-Email");
        String rolesHeader = request.getHeader("X-User-Roles");

        if (userIdHeader != null) {
            try {
                List<Roles> roles = parseRoles(rolesHeader);

                CurrentUser currentUser = new CurrentUser(
                    UUID.fromString(userIdHeader),
                    emailHeader,
                    roles
                );

                var authorities = roles.stream()
                    .map(Roles::getAuthority)
                    .map(SimpleGrantedAuthority::new)
                    .toList();

                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(currentUser, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (IllegalArgumentException e) {
                sendInvalidAuthHeaderResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private List<Roles> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rolesHeader.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .flatMap(role -> Arrays.stream(Roles.values()).filter(r -> r.matches(role)))
            .distinct()
            .toList();
    }

    private void sendInvalidAuthHeaderResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ErrorResponse errorResponse = new ErrorResponse(
            "INVALID_AUTH_HEADER",
            "Invalid X-User-Id format",
            Instant.now(),
            List.of()
        );
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
