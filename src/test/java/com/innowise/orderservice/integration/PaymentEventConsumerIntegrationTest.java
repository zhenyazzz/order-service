package com.innowise.orderservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;
import com.innowise.orderservice.consumer.PaymentCreatedEvent;
import com.innowise.orderservice.consumer.PaymentStatus;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.enums.OrderStatus;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.repository.ProcessedPaymentEventRepository;

@DisplayName("Payment Event Consumer integration tests (Consumer → Service)")
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
class PaymentEventConsumerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID ORDER_PENDING_A = UUID.fromString("aaaaaaaa-0001-4001-8001-000000000001");
    
    @Value("${kafka.topics.payment-events:payment-events}")
    private String paymentEventsTopic;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedPaymentEventRepository processedPaymentEventRepository;

    @BeforeEach
    void resetState() {
        processedPaymentEventRepository.deleteAll();
        Order order = orderRepository.findById(ORDER_PENDING_A).orElseThrow();
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);
    }

    @Test
    @DisplayName("when payment succeeds loads order, sets CONFIRMED, persists order and saves processed row")
    void whenSuccessful_updatesOrderStatusToConfirmed() throws Exception {
        String paymentId = "payment-success-" + UUID.randomUUID();
        publishPaymentEvent(paymentId, ORDER_PENDING_A, PaymentStatus.SUCCESS);

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findById(ORDER_PENDING_A).orElseThrow();
                    assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
                    assertThat(processedPaymentEventRepository.existsById(paymentId)).isTrue();
                });
    }

    @Test
    @DisplayName("when payment fails sets order status to CANCELLED, persists order and saves processed row")
    void whenFailed_setsOrderCancelled() throws Exception {
        String paymentId = "payment-failed-" + UUID.randomUUID();
        publishPaymentEvent(paymentId, ORDER_PENDING_A, PaymentStatus.FAILED);

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findById(ORDER_PENDING_A).orElseThrow();
                    assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(processedPaymentEventRepository.existsById(paymentId)).isTrue();
                });
    }

    @Test
    @DisplayName("when payment id already processed skips processing")
    void whenAlreadyProcessed_skipsProcessing() throws Exception {
        String paymentId = "payment-duplicate-" + UUID.randomUUID();

        publishPaymentEvent(paymentId, ORDER_PENDING_A, PaymentStatus.SUCCESS);
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findById(ORDER_PENDING_A).orElseThrow();
                    assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
                    assertThat(processedPaymentEventRepository.existsById(paymentId)).isTrue();
                });

        Order resetOrder = orderRepository.findById(ORDER_PENDING_A).orElseThrow();
        resetOrder.setStatus(OrderStatus.PENDING);
        orderRepository.save(resetOrder);

        publishPaymentEvent(paymentId, ORDER_PENDING_A, PaymentStatus.FAILED);

        await()
                .during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order updatedOrder = orderRepository.findById(ORDER_PENDING_A).orElseThrow();
                    assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
                });
    }

    @Test
    @DisplayName("when payment event is invalid skips processing")
    void whenInvalidEvent_skipsProcessing() {
        String invalidPayload = "{\"paymentId\":\"bad\",\"orderId\":\"not-a-uuid\",\"status\":\"BROKEN\"}";
        kafkaTemplate.send(paymentEventsTopic, invalidPayload).join();

        await()
                .during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order unchangedOrder = orderRepository.findById(ORDER_PENDING_A).orElseThrow();
                    assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
                    assertThat(processedPaymentEventRepository.count()).isZero();
                });
    }

    private void publishPaymentEvent(String paymentId, UUID orderId, PaymentStatus status) throws JsonProcessingException {
        PaymentCreatedEvent event = new PaymentCreatedEvent(paymentId, orderId.toString(), status);
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(paymentEventsTopic, payload).join();
    }
}
