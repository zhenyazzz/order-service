package com.innowise.internship.orderservice.repository.specification;

import com.innowise.internship.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.internship.orderservice.model.Order;
import com.innowise.internship.orderservice.model.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderSpecification {

    private static final String CREATED_AT_FIELD = "createdAt";

    private OrderSpecification() {
    }

    public static Specification<Order> hasUserId(UUID userId) {
        return (root, query, cb) ->
                userId == null ? cb.conjunction() : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Order> createdAtBetween(Instant startDate, Instant endDate) {
        return (root, query, cb) -> {
            if (startDate == null && endDate == null) {
                return cb.conjunction();
            }
            if (startDate != null && endDate != null) {
                return cb.between(root.get(CREATED_AT_FIELD), startDate, endDate);
            }
            if (startDate != null) {
                return cb.greaterThanOrEqualTo(root.get(CREATED_AT_FIELD), startDate);
            }
            return cb.lessThanOrEqualTo(root.get(CREATED_AT_FIELD), endDate);
        };
    }

    public static Specification<Order> hasStatuses(List<OrderStatus> statuses) {
        return (root, query, cb) ->
                (statuses == null || statuses.isEmpty()) ? cb.conjunction() : root.get("status").in(statuses);
    }

    public static Specification<Order> byFilter(OrderSearchFilterRequest filter) {
        if (filter == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return Specification.where(createdAtBetween(filter.createdFrom(), filter.createdTo()))
                .and(hasStatuses(filter.statuses()));
    }
}
