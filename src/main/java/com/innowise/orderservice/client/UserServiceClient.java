package com.innowise.orderservice.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.innowise.orderservice.dto.internal.UserResponse;

@FeignClient(name = "user-service", url = "${user-service.url}")
public interface UserServiceClient {

    @GetMapping("/users/internal/{id}")
    UserResponse getInternalUserById(@PathVariable("id") UUID id);

    @PostMapping("/users/internal/by-ids")
    List<UserResponse> getInternalUsersByIds(@RequestBody List<UUID> ids);
}
