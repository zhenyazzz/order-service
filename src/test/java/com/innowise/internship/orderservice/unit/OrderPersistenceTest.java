package com.innowise.internship.orderservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.innowise.internship.orderservice.dto.internal.UserResponse;
import com.innowise.internship.orderservice.dto.request.CreateOrderRequest;
import com.innowise.internship.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.internship.orderservice.dto.response.OrderResponse;
import com.innowise.internship.orderservice.exception.InvalidOrderStateException;
import com.innowise.internship.orderservice.exception.OrderNotFoundException;
import com.innowise.internship.orderservice.mapper.OrderItemMapper;
import com.innowise.internship.orderservice.mapper.OrderMapper;
import com.innowise.internship.orderservice.model.Item;
import com.innowise.internship.orderservice.model.Order;
import com.innowise.internship.orderservice.model.OrderItem;
import com.innowise.internship.orderservice.model.enums.OrderStatus;
import com.innowise.internship.orderservice.repository.ItemRepository;
import com.innowise.internship.orderservice.repository.OrderRepository;
import com.innowise.internship.orderservice.service.OrderPersistence;
import com.innowise.internship.orderservice.utils.OrderTestDataFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPersistence")
class OrderPersistenceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @InjectMocks
    private OrderPersistence orderPersistence;

    @Nested
    @DisplayName("findByIdAndUserId")
    class FindByIdAndUserId {

        @Test
        @DisplayName("when order exists and is PENDING returns order")
        void whenPending_returnsOrder() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);

            when(orderRepository.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .thenReturn(Optional.of(order));

            Order result = orderPersistence.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID);

            assertThat(result).isEqualTo(order);
        }

        @Test
        @DisplayName("when order not found throws OrderNotFoundException")
        void whenNotFound_throws() {
            when(orderRepository.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderPersistence.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("when order is CONFIRMED throws InvalidOrderStateException")
        void whenConfirmed_throwsInvalidState() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.CONFIRMED);

            when(orderRepository.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderPersistence.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Only PENDING orders can be modified");
        }

        @Test
        @DisplayName("when order is CANCELLED throws InvalidOrderStateException")
        void whenCancelled_throwsInvalidState() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.CANCELLED);

            when(orderRepository.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderPersistence.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("delegates to repository with spec and pageable")
        @SuppressWarnings("unchecked")
        void delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Specification<Order> spec = (root, query, criteriaBuilder) -> null;
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            Page<Order> page = new PageImpl<>(List.of(order));

            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            Page<Order> result = orderPersistence.findAll(spec, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository).findAll(spec, pageable);
        }
    }

    @Nested
    @DisplayName("saveNewOrder")
    class SaveNewOrder {

        @Test
        @DisplayName("creates order with items and calculates total price")
        void createsOrderWithItems() {
            UserResponse userResponse = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, "test@example.com");
            CreateOrderRequest request = OrderTestDataFactory.buildCreateOrderRequest();
            Item item = new Item();
            item.setId(OrderTestDataFactory.ITEM_ID);
            item.setPrice(BigDecimal.TEN);
            Order order = new Order();
            OrderResponse response = OrderTestDataFactory.buildOrderResponse();

            when(itemRepository.findAllById(any())).thenReturn(List.of(item));
            when(orderItemMapper.toEntity(any(), any())).thenReturn(new OrderItem());
            when(orderMapper.toEntity(any(UUID.class), any())).thenReturn(order);
            when(orderMapper.toResponse(order)).thenReturn(response);

            OrderResponse result = orderPersistence.saveNewOrder(userResponse, request);

            assertThat(result).isEqualTo(response);
            verify(orderRepository).save(order);
        }
    }

    @Nested
    @DisplayName("updateOrder")
    class UpdateOrder {

        @Test
        @DisplayName("clears items and sets new items with recalculated price")
        void updatesItemsAndPrice() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();
            Item item = new Item();
            item.setId(OrderTestDataFactory.ITEM_ID);
            item.setPrice(BigDecimal.valueOf(25.50));

            when(orderRepository.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(Optional.of(order));
            when(itemRepository.findAllById(any())).thenReturn(List.of(item));
            when(orderItemMapper.toEntity(any(), any())).thenAnswer(inv -> {
                OrderItem oi = new OrderItem();
                oi.setItem(item);
                oi.setQuantity(2);
                return oi;
            });
            when(orderRepository.save(order)).thenReturn(order);

            Order result = orderPersistence.updateOrder(OrderTestDataFactory.ORDER_ID, request);

            assertThat(result.getTotalPrice()).isEqualTo(BigDecimal.valueOf(51.00));
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("when order not found throws OrderNotFoundException")
        void whenNotFound_throws() {
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();

            when(orderRepository.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderPersistence.updateOrder(OrderTestDataFactory.ORDER_ID, request))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("when order is CONFIRMED throws InvalidOrderStateException")
        void whenConfirmed_throwsInvalidState() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.CONFIRMED);
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();

            when(orderRepository.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderPersistence.updateOrder(OrderTestDataFactory.ORDER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Only PENDING orders can be modified");
        }

        @Test
        @DisplayName("when empty items throws InvalidOrderStateException")
        void whenEmptyItems_throws() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UpdateOrderRequest request = new UpdateOrderRequest(List.of());

            when(orderRepository.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderPersistence.updateOrder(OrderTestDataFactory.ORDER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("at least one item");
        }
    }
}
