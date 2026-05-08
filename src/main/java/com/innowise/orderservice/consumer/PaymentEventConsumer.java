package com.innowise.orderservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
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
    public void consumePaymentEvent(
        String messagePayload,
        @Header(value = "event_type") String eventType,
        @Header(value = "event_id") String eventId
        ) {
        if (eventType == null || eventType.isBlank()) {
            log.warn("Skipping message without event_type header. eventId={}", eventId);
            return;
        }

        switch (eventType) {
            case "PAYMENT_CREATED" -> {
                CreatePaymentEvent createPaymentEvent = objectMapper.readValue(
                    messagePayload, CreatePaymentEvent.class
                );
                log.info("Processing payment event: {}", createPaymentEvent);
                orderService.processPaymentEvent(createPaymentEvent);
            }
            default -> {
                log.warn("Skipping unknown event type: {}", eventType);
            }
        }
    }
}
