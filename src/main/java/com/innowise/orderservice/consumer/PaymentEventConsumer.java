package com.innowise.orderservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.innowise.orderservice.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${kafka.topics.payment-events:payment-events}", 
        groupId = "${spring.kafka.consumer.group-id:order-service-group}"
    )
    public void consumePaymentEvent(String messagePayload) {
        PaymentCreatedEvent paymentCreatedEvent = objectMapper.readValue(
            messagePayload, PaymentCreatedEvent.class
        );
        
        log.info("Processing payment event: {}", paymentCreatedEvent);

        orderService.processPaymentEvent(paymentCreatedEvent);
    }
}
