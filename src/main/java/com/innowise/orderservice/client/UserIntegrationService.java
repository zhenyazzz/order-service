package com.innowise.orderservice.client;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.exception.integration.UserServiceUnavailableException;
import com.innowise.orderservice.exception.notfound.UserNotFoundException;

import feign.FeignException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserIntegrationService {

    private final UserServiceClient userServiceClient;

    @CircuitBreaker(name = "user-service")
    @Retry(name = "user-service", fallbackMethod = "getInternalUserByIdFallback")
    public UserResponse getInternalUserById(UUID id) {
        try {
            return userServiceClient.getInternalUserById(id);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new UserNotFoundException("User not found for id: " + id);
            }
            throw e;
        }
    }

    @CircuitBreaker(name = "user-service")
    @Retry(name = "user-service", fallbackMethod = "getInternalUsersByIdsFallback")
    public List<UserResponse> getInternalUsersByIds(List<UUID> ids) {
        return userServiceClient.getInternalUsersByIds(ids);
    }

    private UserResponse getInternalUserByIdFallback(UUID id, Throwable throwable) {
        if (throwable instanceof UserNotFoundException userNotFoundException) {
            throw userNotFoundException;
        }

        if (throwable instanceof feign.FeignException feignException && feignException.status() == 404) {
            throw new UserNotFoundException("User not found for id: " + id);
        }

        log.warn("User service call failed for userId={}, falling back: {}", id, throwable.toString());
        throw new UserServiceUnavailableException(
                "User service is unavailable. Please retry later.",
                throwable
        );
    }

    private List<UserResponse> getInternalUsersByIdsFallback(List<UUID> ids, Throwable throwable) {
        log.warn("User service batch call failed, falling back: {}", throwable.toString());
        throw new UserServiceUnavailableException(
                "User service is unavailable. Please retry later.",
                throwable
        );
    }
}
