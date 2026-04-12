package com.innowise.orderservice.dto.request;

import com.innowise.orderservice.model.enums.OrderStatus;
import com.innowise.orderservice.validation.annotation.ValidDateRange;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

@ValidDateRange
public record OrderSearchFilterRequest(
        @PastOrPresent(message = "Created from date must be in the past or present")
        Instant createdFrom,
        @PastOrPresent(message = "Created to date must be in the past or present")
        Instant createdTo,
        @Valid
        List<@NotNull(message = "Status must not be null") OrderStatus> statuses
) {
}
