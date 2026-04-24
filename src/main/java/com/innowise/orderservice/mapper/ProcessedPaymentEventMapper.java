package com.innowise.orderservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.innowise.orderservice.consumer.PaymentCreatedEvent;
import com.innowise.orderservice.model.ProcessedPaymentEvent;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface ProcessedPaymentEventMapper {

    @Mapping(target = "processedAt", ignore = true)
    ProcessedPaymentEvent toEntity(PaymentCreatedEvent event);
}
