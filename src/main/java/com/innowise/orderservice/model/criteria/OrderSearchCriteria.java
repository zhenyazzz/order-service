package com.innowise.orderservice.model.criteria;

import com.innowise.orderservice.model.enums.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderSearchCriteria(
        Instant createdFrom,
        Instant createdTo,
        List<OrderStatus> statuses
) {
}
