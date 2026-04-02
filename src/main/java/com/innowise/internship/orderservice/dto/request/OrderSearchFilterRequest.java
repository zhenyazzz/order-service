package com.innowise.internship.orderservice.dto.request;

import com.innowise.internship.orderservice.model.enums.OrderStatus;
import com.innowise.internship.orderservice.validation.annotation.ValidDateRange;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.PastOrPresent;

@ValidDateRange
public record OrderSearchFilterRequest(
        @PastOrPresent(message = "Created from date must be in the past or present")
        Instant createdFrom,
        @PastOrPresent(message = "Created to date must be in the past or present")
        Instant createdTo,
        List<OrderStatus> statuses
) {
}
