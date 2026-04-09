package com.innowise.orderservice.unit;

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

import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.exception.conflict.InvalidOrderStateException;
import com.innowise.orderservice.exception.notfound.ItemNotFoundException;
import com.innowise.orderservice.exception.notfound.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.enums.OrderStatus;
import com.innowise.orderservice.persistence.OrderPersistence;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.utils.OrderTestDataFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPersistence (unit tests)")
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
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("when order exists returns order")
        void whenFound_returnsOrder() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);

            when(orderRepository.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(Optional.of(order));

            Order result = orderPersistence.findById(OrderTestDataFactory.ORDER_ID);

            assertThat(result).isEqualTo(order);
        }

        @Test
        @DisplayName("when order not found throws OrderNotFoundException")
        void whenNotFound_throws() {
            when(orderRepository.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderPersistence.findById(OrderTestDataFactory.ORDER_ID))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByIdAndUserId")
    class FindByIdAndUserId {

        @Test
        @DisplayName("when order exists returns order")
        void whenFound_returnsOrder() {
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
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("delegates to repository and returns saved entity")
        void delegatesToRepository() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            when(orderRepository.save(order)).thenReturn(order);

            Order result = orderPersistence.save(order);

            assertThat(result).isEqualTo(order);
            verify(orderRepository).save(order);
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("returns true when row was updated")
        void whenUpdated_returnsTrue() {
            when(orderRepository.updateStatus(OrderTestDataFactory.ORDER_ID, OrderStatus.CONFIRMED, 0L))
                    .thenReturn(1);

            boolean result = orderPersistence.updateStatus(OrderTestDataFactory.ORDER_ID, OrderStatus.CONFIRMED, 0L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when optimistic lock prevented update")
        void whenNoRowUpdated_returnsFalse() {
            when(orderRepository.updateStatus(OrderTestDataFactory.ORDER_ID, OrderStatus.CONFIRMED, 0L))
                    .thenReturn(0);

            boolean result = orderPersistence.updateStatus(OrderTestDataFactory.ORDER_ID, OrderStatus.CONFIRMED, 0L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("loads order and deletes")
        void whenFound_deletes() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            when(orderRepository.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(Optional.of(order));

            orderPersistence.deleteById(OrderTestDataFactory.ORDER_ID);

            verify(orderRepository).delete(order);
        }

        @Test
        @DisplayName("when order not found throws OrderNotFoundException")
        void whenNotFound_throws() {
            when(orderRepository.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderPersistence.deleteById(OrderTestDataFactory.ORDER_ID))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("saveNewOrder")
    class SaveNewOrder {

        @Test
        @DisplayName("creates order with items and saves")
        void createsOrderWithItems() {
            UserResponse userResponse = OrderTestDataFactory.buildUserResponse();
            CreateOrderRequest request = OrderTestDataFactory.buildCreateOrderRequest();
            Item item = new Item();
            item.setId(OrderTestDataFactory.ITEM_ID);
            item.setPrice(BigDecimal.TEN);
            Order order = new Order();

            when(itemRepository.findAllById(any())).thenReturn(List.of(item));
            when(orderItemMapper.toEntity(any(), any())).thenReturn(new OrderItem());
            when(orderMapper.toEntity(any(UUID.class), any())).thenReturn(order);

            Order result = orderPersistence.saveNewOrder(userResponse, request);

            assertThat(result).isEqualTo(order);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("when item id not in catalog throws ItemNotFoundException")
        void whenItemMissing_throws() {
            UserResponse userResponse = OrderTestDataFactory.buildUserResponse();
            CreateOrderRequest request = OrderTestDataFactory.buildCreateOrderRequest();

            when(itemRepository.findAllById(any())).thenReturn(List.of());

            assertThatThrownBy(() -> orderPersistence.saveNewOrder(userResponse, request))
                    .isInstanceOf(ItemNotFoundException.class)
                    .hasMessageContaining(OrderTestDataFactory.ITEM_ID.toString());
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

            when(itemRepository.findAllById(any())).thenReturn(List.of(item));
            when(orderItemMapper.toEntity(any(), any())).thenAnswer(inv -> {
                OrderItem oi = new OrderItem();
                oi.setItem(item);
                oi.setQuantity(2);
                return oi;
            });
            when(orderRepository.save(order)).thenReturn(order);

            Order result = orderPersistence.updateOrder(order, request);

            assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(51.00));
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("when item id not in catalog throws ItemNotFoundException")
        void whenItemMissing_throws() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();

            when(itemRepository.findAllById(any())).thenReturn(List.of());

            assertThatThrownBy(() -> orderPersistence.updateOrder(order, request))
                    .isInstanceOf(ItemNotFoundException.class)
                    .hasMessageContaining(OrderTestDataFactory.ITEM_ID.toString());
        }

        @Test
        @DisplayName("when empty items throws InvalidOrderStateException")
        void whenEmptyItems_throws() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UpdateOrderRequest request = new UpdateOrderRequest(List.of());

            when(itemRepository.findAllById(any())).thenReturn(List.of());

            assertThatThrownBy(() -> orderPersistence.updateOrder(order, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("at least one item");
        }

        @Test
        @DisplayName("when request references unknown item after distinct ids, throws ItemNotFoundException")
        void whenPartialItemsFromRepo_throwsForMissing() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UUID otherId = UUID.fromString("22222222-2222-4222-8222-222222222222");
            UpdateOrderRequest request = new UpdateOrderRequest(
                    List.of(
                            new OrderItemRequest(OrderTestDataFactory.ITEM_ID, 1),
                            new OrderItemRequest(otherId, 1)));

            Item only = new Item();
            only.setId(OrderTestDataFactory.ITEM_ID);
            only.setPrice(BigDecimal.ONE);
            when(itemRepository.findAllById(any())).thenReturn(List.of(only));
            when(orderItemMapper.toEntity(any(), any())).thenAnswer(inv -> {
                OrderItem oi = new OrderItem();
                oi.setItem(inv.getArgument(1));
                oi.setQuantity(((OrderItemRequest) inv.getArgument(0)).quantity());
                return oi;
            });

            assertThatThrownBy(() -> orderPersistence.updateOrder(order, request))
                    .isInstanceOf(ItemNotFoundException.class)
                    .hasMessageContaining(otherId.toString());
        }
    }
}
