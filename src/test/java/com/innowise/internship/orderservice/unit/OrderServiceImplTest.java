package com.innowise.internship.orderservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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
import com.innowise.internship.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.internship.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.internship.orderservice.dto.response.OrderResponse;
import com.innowise.internship.orderservice.exception.conflict.OrderAlreadyCancelledException;
import com.innowise.internship.orderservice.exception.notfound.OrderNotFoundException;
import com.innowise.internship.orderservice.exception.notfound.UserNotFoundException;
import com.innowise.internship.orderservice.exception.security.SecurityContextException;
import com.innowise.internship.orderservice.mapper.OrderMapper;
import com.innowise.internship.orderservice.model.Order;
import com.innowise.internship.orderservice.model.enums.OrderStatus;
import com.innowise.internship.orderservice.service.OrderPersistence;
import com.innowise.internship.orderservice.service.impl.OrderServiceImpl;
import com.innowise.internship.orderservice.client.UserIntegrationService;
import com.innowise.internship.orderservice.utils.OrderTestDataFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl")
class OrderServiceImplTest {

    @Mock
    private UserIntegrationService userIntegrationService;

    @Mock
    private OrderPersistence orderPersistence;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("when user exists delegates to persistence and returns response")
        void whenUserFound_returnsFromPersistence() {
            CreateOrderRequest request = OrderTestDataFactory.buildCreateOrderRequest();
            OrderResponse expected = OrderTestDataFactory.buildOrderResponse();
            UserResponse user = new UserResponse(
                    OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);

            when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
            when(orderPersistence.saveNewOrder(user, request)).thenReturn(expected);

            OrderResponse result = orderService.createOrder(OrderTestDataFactory.USER_ID, request);

            assertThat(result).isEqualTo(expected);
            verify(orderPersistence).saveNewOrder(user, request);
        }

        @Test
        @DisplayName("when user integration throws UserNotFoundException does not persist")
        void whenUserMissing_throws() {
            CreateOrderRequest request = OrderTestDataFactory.buildCreateOrderRequest();

            when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID))
                    .thenThrow(new UserNotFoundException("User not found for id: " + OrderTestDataFactory.USER_ID));

            assertThatThrownBy(() -> orderService.createOrder(OrderTestDataFactory.USER_ID, request))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("User not found");

            verify(orderPersistence, never()).saveNewOrder(any(), any());
        }
    }

    @Nested
    @DisplayName("getMyOrderById")
    class GetMyOrderById {

        @Test
        @DisplayName("when order belongs to user returns mapped response with user")
        void whenFound_returnsResponse() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UserResponse user = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);
            OrderResponse mapped = OrderTestDataFactory.buildOrderResponse(order);

            when(orderPersistence.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .thenReturn(order);
            when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(mapped);

            OrderResponse result = orderService.getMyOrderById(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID);

            assertThat(result).isEqualTo(mapped);
        }

        @Test
        @DisplayName("when order missing throws OrderNotFoundException")
        void whenMissing_throws() {
            when(orderPersistence.findByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .thenThrow(new OrderNotFoundException("Order not found"));

            assertThatThrownBy(() -> orderService.getMyOrderById(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    @Nested
    @DisplayName("getOrderById")
    class GetOrderById {

        @Test
        @DisplayName("when order exists returns mapped response with user")
        void whenFound_returnsResponse() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.CONFIRMED);
            UserResponse user = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);
            OrderResponse mapped = OrderTestDataFactory.buildOrderResponse(order);

            when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);
            when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(mapped);

            assertThat(orderService.getOrderById(OrderTestDataFactory.ORDER_ID)).isEqualTo(mapped);
        }

        @Test
        @DisplayName("when order missing throws OrderNotFoundException")
        void whenMissing_throws() {
            when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID))
                    .thenThrow(new OrderNotFoundException("Order not found"));

            assertThatThrownBy(() -> orderService.getOrderById(OrderTestDataFactory.ORDER_ID))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getOrders (admin)")
    class GetOrders {

        @Test
        @DisplayName("maps page via repository findAll with Specification and enriches users")
        void returnsPage() {
            OrderSearchFilterRequest filter = OrderTestDataFactory.emptyFilter();
            Pageable pageable = PageRequest.of(0, 10);
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UserResponse user = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);
            OrderResponse row = OrderTestDataFactory.buildOrderResponse(order);
            Page<Order> entityPage = new PageImpl<>(List.of(order), pageable, 1);

            when(orderPersistence.findAll(ArgumentMatchers.<Specification<Order>>any(), eq(pageable)))
                    .thenReturn(entityPage);
            when(userIntegrationService.getInternalUsersByIds(List.of(OrderTestDataFactory.USER_ID)))
                    .thenReturn(List.of(user));
            when(orderMapper.toResponse(order, user)).thenReturn(row);

            Page<OrderResponse> page = orderService.getOrders(filter, pageable);

            assertThat(page.getContent()).containsExactly(row);
            assertThat(page.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getMyOrders / getOrdersByUserId")
    class GetMyOrders {

        @Test
        @DisplayName("getMyOrders applies user filter and enriches users")
        void getMyOrders_usesUserId() {
            OrderSearchFilterRequest filter = OrderTestDataFactory.emptyFilter();
            Pageable pageable = PageRequest.of(0, 5);
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UserResponse user = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);
            OrderResponse row = OrderTestDataFactory.buildOrderResponse(order);
            Page<Order> entityPage = new PageImpl<>(List.of(order), pageable, 1);

            when(orderPersistence.findAll(ArgumentMatchers.<Specification<Order>>any(), eq(pageable)))
                    .thenReturn(entityPage);
            when(userIntegrationService.getInternalUsersByIds(List.of(OrderTestDataFactory.USER_ID)))
                    .thenReturn(List.of(user));
            when(orderMapper.toResponse(order, user)).thenReturn(row);

            Page<OrderResponse> page = orderService.getMyOrders(filter, pageable, OrderTestDataFactory.USER_ID);

            assertThat(page.getContent()).containsExactly(row);
        }

        @Test
        @DisplayName("getOrdersByUserId applies same path with user id and enriches users")
        void getOrdersByUserId_usesUserId() {
            OrderSearchFilterRequest filter = OrderTestDataFactory.emptyFilter();
            Pageable pageable = PageRequest.of(0, 5);
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UserResponse user = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);
            OrderResponse row = OrderTestDataFactory.buildOrderResponse(order);
            Page<Order> entityPage = new PageImpl<>(List.of(order), pageable, 1);

            when(orderPersistence.findAll(ArgumentMatchers.<Specification<Order>>any(), eq(pageable)))
                    .thenReturn(entityPage);
            when(userIntegrationService.getInternalUsersByIds(List.of(OrderTestDataFactory.USER_ID)))
                    .thenReturn(List.of(user));
            when(orderMapper.toResponse(order, user)).thenReturn(row);

            Page<OrderResponse> page = orderService.getOrdersByUserId(filter, pageable, OrderTestDataFactory.USER_ID);

            assertThat(page.getContent()).containsExactly(row);
        }
    }

    @Nested
    @DisplayName("updateMyOrder")
    class UpdateMyOrder {

        @Test
        @DisplayName("when order exists and is pending updates order and returns response")
        void whenPending_updatesAndReturnsResponse() {
            UserResponse user = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);
            Order updatedOrder = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            OrderResponse out = OrderTestDataFactory.buildOrderResponse(updatedOrder);
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();

            when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
            when(orderPersistence.updateOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request))
                    .thenReturn(updatedOrder);
            when(orderMapper.toResponse(updatedOrder, user)).thenReturn(out);

            OrderResponse result = orderService.updateMyOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request);

            assertThat(result).isEqualTo(out);
            verify(orderPersistence).updateOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request);
            verify(userIntegrationService).getInternalUserById(OrderTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("when order not found throws OrderNotFoundException")
        void whenNotFound_throws() {
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();

            when(orderPersistence.updateOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request))
                    .thenThrow(new OrderNotFoundException("Order not found"));

            assertThatThrownBy(() -> orderService.updateMyOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(orderPersistence).updateOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request);
            verify(userIntegrationService, never()).getInternalUserById(any());
        }

        @Test
        @DisplayName("when current user is missing throws SecurityContextException")
        void whenCurrentUserMissing_throws() {
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();

            assertThatThrownBy(() -> orderService.updateMyOrder(OrderTestDataFactory.ORDER_ID, null, request))
                    .isInstanceOf(SecurityContextException.class)
                    .hasMessageContaining("not authenticated");

            verify(orderPersistence, never()).updateOrder(any(), any(), any());
            verify(userIntegrationService, never()).getInternalUserById(any());
        }
    }

    @Nested
    @DisplayName("cancelMyOrder / cancelOrder")
    class Cancel {

        @Test
        @DisplayName("cancelMyOrder: loads by id and user, persists when not already cancelled")
        void cancelMyOrder_whenPending_savesCancelled() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            OrderResponse out = OrderTestDataFactory.buildOrderResponse(OrderTestDataFactory.buildOrder(OrderStatus.CANCELLED));
            UserResponse user = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);

            when(orderPersistence.cancelByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .thenReturn(order);
            when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
            when(orderMapper.toResponse(any(Order.class), eq(user))).thenReturn(out);

            OrderResponse result = orderService.cancelMyOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID);

            assertThat(result).isEqualTo(out);
            verify(orderPersistence).cancelByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID);
            verify(userIntegrationService).getInternalUserById(OrderTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("cancelMyOrder: when already cancelled throws OrderAlreadyCancelledException")
        void cancelMyOrder_whenAlreadyCancelled_throws() {
            when(orderPersistence.cancelByIdAndUserId(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .thenThrow(new OrderAlreadyCancelledException("Order is already cancelled"));

            assertThatThrownBy(() -> orderService.cancelMyOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .isInstanceOf(OrderAlreadyCancelledException.class)
                    .hasMessageContaining("already cancelled");
        }

        @Test
        @DisplayName("cancelOrder (admin): same behaviour for pending")
        void cancelOrder_whenPending_saves() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            OrderResponse out = OrderTestDataFactory.buildOrderResponse(OrderTestDataFactory.buildOrder(OrderStatus.CANCELLED));
            UserResponse user = new UserResponse(OrderTestDataFactory.USER_ID, "Test", "User", null, OrderTestDataFactory.USER_EMAIL);

            when(orderPersistence.cancelById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);
            when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
            when(orderMapper.toResponse(any(Order.class), eq(user))).thenReturn(out);

            OrderResponse result = orderService.cancelOrder(OrderTestDataFactory.ORDER_ID);

            assertThat(result).isEqualTo(out);
            verify(orderPersistence).cancelById(OrderTestDataFactory.ORDER_ID);
            verify(userIntegrationService).getInternalUserById(OrderTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("cancelOrder (admin): when already cancelled throws OrderAlreadyCancelledException")
        void cancelOrder_whenAlreadyCancelled_throws() {
            when(orderPersistence.cancelById(OrderTestDataFactory.ORDER_ID))
                    .thenThrow(new OrderAlreadyCancelledException("Order is already cancelled"));

            assertThatThrownBy(() -> orderService.cancelOrder(OrderTestDataFactory.ORDER_ID))
                    .isInstanceOf(OrderAlreadyCancelledException.class);
        }
    }

    @Nested
    @DisplayName("deleteOrder")
    class DeleteOrder {

        @Test
        @DisplayName("when order exists calls repository delete")
        void whenFound_deletes() {
            doNothing().when(orderPersistence).deleteById(OrderTestDataFactory.ORDER_ID);

            orderService.deleteOrder(OrderTestDataFactory.ORDER_ID);

            verify(orderPersistence).deleteById(OrderTestDataFactory.ORDER_ID);
        }

        @Test
        @DisplayName("when order missing throws OrderNotFoundException")
        void whenMissing_throws() {
            doThrow(new OrderNotFoundException("Order not found"))
                    .when(orderPersistence).deleteById(OrderTestDataFactory.ORDER_ID);

            assertThatThrownBy(() -> orderService.deleteOrder(OrderTestDataFactory.ORDER_ID))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(orderPersistence).deleteById(OrderTestDataFactory.ORDER_ID);
        }
    }
}
