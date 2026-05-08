package com.innowise.orderservice.dto.internal;

import java.math.BigDecimal;

public record InternalOrderPriceResponse(
    BigDecimal totalPrice
) {

}
