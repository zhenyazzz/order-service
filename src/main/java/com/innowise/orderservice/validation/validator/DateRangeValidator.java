package com.innowise.orderservice.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.innowise.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.orderservice.validation.annotation.ValidDateRange;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, OrderSearchFilterRequest> {

    @Override
    public boolean isValid(OrderSearchFilterRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.createdFrom() == null && value.createdTo() == null) {
            return true;
        }

        if (value.createdFrom() == null || value.createdTo() == null) {
            return false;
        }

        if (value.createdFrom().isAfter(value.createdTo())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Created from must not be after created to date")
                    .addPropertyNode("createdTo")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
