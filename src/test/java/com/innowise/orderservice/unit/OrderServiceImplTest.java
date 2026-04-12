package com.innowise.orderservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.innowise.orderservice.application.order.CreateOrderCommand;
import com.innowise.orderservice.application.order.OrderCommandMapperImpl;
import com.innowise.orderservice.application.order.UpdateOrderItemsCommand;
import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderSearchFilterRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderStatusRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.exception.conflict.InvalidOrderStateException;
import com.innowise.orderservice.exception.notfound.OrderNotFoundException;
import com.innowise.orderservice.exception.notfound.UserNotFoundException;
import com.innowise.orderservice.exception.security.ForbiddenException;
import com.innowise.orderservice.exception.security.SecurityContextException;
import com.innowise.orderservice.mapper.OrderCommandMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.enums.OrderStatus;
import com.innowise.orderservice.persistence.OrderPersistence;
import com.innowise.orderservice.security.SecurityUtils;
import com.innowise.orderservice.service.impl.OrderServiceImpl;
import com.innowise.orderservice.client.UserIntegrationService;
import com.innowise.orderservice.utils.OrderTestDataFactory;

import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl (unit tests)")
class OrderServiceImplTest {

    @Mock
    private UserIntegrationService userIntegrationService;

    @Mock
    private OrderPersistence orderPersistence;

    @Mock
    private OrderMapper orderMapper;

    @Spy
    private OrderCommandMapper orderCommandMapper = new OrderCommandMapperImpl();

    @InjectMocks
    private OrderServiceImpl orderService;

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("when user exists delegates to persistence and maps saved order to response")
        void whenUserFound_returnsMappedResponse() {
            CreateOrderRequest request = OrderTestDataFactory.buildCreateOrderRequest();
            OrderResponse expected = OrderTestDataFactory.buildOrderResponse();
            UserResponse user = OrderTestDataFactory.buildUserResponse();
            Order saved = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);

            when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
            when(orderPersistence.saveNewOrder(any(CreateOrderCommand.class))).thenReturn(saved);
            when(orderMapper.toResponse(saved, user)).thenReturn(expected);

            OrderResponse result = orderService.createOrder(OrderTestDataFactory.USER_ID, request);

            assertThat(result).isEqualTo(expected);
            verify(orderPersistence).saveNewOrder(any(CreateOrderCommand.class));
            verify(userIntegrationService).getInternalUserById(OrderTestDataFactory.USER_ID);
            verify(orderMapper).toResponse(saved, user);
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

            verify(orderPersistence, never()).saveNewOrder(any(CreateOrderCommand.class));
        }
    }

    @Nested
    @DisplayName("getOrderById")
    class GetOrderById {

        @Test
        @DisplayName("when order exists and caller is owner returns mapped response with user")
        void whenFoundAsOwner_returnsResponse() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UserResponse user = OrderTestDataFactory.buildUserResponse();
            OrderResponse mapped = OrderTestDataFactory.buildOrderResponse(order);

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

                when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);
                when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
                when(orderMapper.toResponse(order, user)).thenReturn(mapped);

                OrderResponse result = orderService.getOrderById(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID);

                assertThat(result).isEqualTo(mapped);
            }
        }

        @Test
        @DisplayName("when order missing throws OrderNotFoundException")
        void whenMissing_throws() {
            when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID))
                    .thenThrow(new OrderNotFoundException("Order not found"));

            assertThatThrownBy(() -> orderService.getOrderById(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("when caller is not owner and not admin throws ForbiddenException")
        void whenNotOwnerAndNotAdmin_throwsForbidden() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

                when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);

                assertThatThrownBy(() -> orderService.getOrderById(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.OTHER_USER_ID))
                        .isInstanceOf(ForbiddenException.class)
                        .hasMessageContaining("No access");
            }
        }
    }

    @Nested
    @DisplayName("getOrders")
    class GetOrders {

        @Test
        @DisplayName("when admin maps page without scoping to current user id")
        void whenAdmin_returnsPage() {
            OrderSearchFilterRequest filter = OrderTestDataFactory.emptyFilter();
            Pageable pageable = PageRequest.of(0, 10);
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UserResponse user = OrderTestDataFactory.buildUserResponse();
            OrderResponse row = OrderTestDataFactory.buildOrderResponse(order);
            Page<Order> entityPage = new PageImpl<>(List.of(order), pageable, 1);

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(true);

                when(orderPersistence.findAll(ArgumentMatchers.<Specification<Order>>any(), eq(pageable)))
                        .thenReturn(entityPage);
                when(userIntegrationService.getInternalUsersByIds(List.of(OrderTestDataFactory.USER_ID)))
                        .thenReturn(List.of(user));
                when(orderMapper.toResponse(order, user)).thenReturn(row);

                Page<OrderResponse> page = orderService.getOrders(filter, pageable, OrderTestDataFactory.USER_ID);

                assertThat(page.getContent()).containsExactly(row);
                assertThat(page.getTotalElements()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("when not admin scopes listing to current user id")
        void whenNotAdmin_usesCurrentUserId() {
            OrderSearchFilterRequest filter = OrderTestDataFactory.emptyFilter();
            Pageable pageable = PageRequest.of(0, 5);
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UserResponse user = OrderTestDataFactory.buildUserResponse();
            OrderResponse row = OrderTestDataFactory.buildOrderResponse(order);
            Page<Order> entityPage = new PageImpl<>(List.of(order), pageable, 1);

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

                when(orderPersistence.findAll(ArgumentMatchers.<Specification<Order>>any(), eq(pageable)))
                        .thenReturn(entityPage);
                when(userIntegrationService.getInternalUsersByIds(List.of(OrderTestDataFactory.USER_ID)))
                        .thenReturn(List.of(user));
                when(orderMapper.toResponse(order, user)).thenReturn(row);

                Page<OrderResponse> page = orderService.getOrders(filter, pageable, OrderTestDataFactory.USER_ID);

                assertThat(page.getContent()).containsExactly(row);
            }
        }
    }

    @Nested
    @DisplayName("getOrdersByUserId")
    class GetOrdersByUserId {

        @Test
        @DisplayName("applies user id filter and enriches users")
        void usesUserId() {
            OrderSearchFilterRequest filter = OrderTestDataFactory.emptyFilter();
            Pageable pageable = PageRequest.of(0, 5);
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            UserResponse user = OrderTestDataFactory.buildUserResponse();
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
    @DisplayName("updateOrder")
    class UpdateOrder {

        @Test
        @DisplayName("when order exists and caller is owner updates order and returns response")
        void whenOwner_updatesAndReturnsResponse() {
            UserResponse user = OrderTestDataFactory.buildUserResponse();
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            Order updatedOrder = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            OrderResponse out = OrderTestDataFactory.buildOrderResponse(updatedOrder);
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

                when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);
                when(orderPersistence.updateOrder(eq(order), any(UpdateOrderItemsCommand.class))).thenReturn(updatedOrder);
                when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
                when(orderMapper.toResponse(updatedOrder, user)).thenReturn(out);

                OrderResponse result = orderService.updateOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request);

                assertThat(result).isEqualTo(out);
                verify(orderPersistence).updateOrder(eq(order), any(UpdateOrderItemsCommand.class));
                verify(userIntegrationService).getInternalUserById(OrderTestDataFactory.USER_ID);
            }
        }

        @Test
        @DisplayName("when order not found throws OrderNotFoundException")
        void whenNotFound_throws() {
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();

            when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID))
                    .thenThrow(new OrderNotFoundException("Order not found"));

            assertThatThrownBy(() -> orderService.updateOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request))
                    .isInstanceOf(OrderNotFoundException.class);

            verify(orderPersistence).findById(OrderTestDataFactory.ORDER_ID);
            verify(orderPersistence, never()).updateOrder(any(), any());
            verify(userIntegrationService, never()).getInternalUserById(any());
        }

        @Test
        @DisplayName("when order is not PENDING throws InvalidOrderStateException")
        void whenNotPending_throwsInvalidState() {
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.CONFIRMED);

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

                when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);

                assertThatThrownBy(() -> orderService.updateOrder(OrderTestDataFactory.ORDER_ID, OrderTestDataFactory.USER_ID, request))
                        .isInstanceOf(InvalidOrderStateException.class)
                        .hasMessageContaining("PENDING");

                verify(orderPersistence, never()).updateOrder(any(), any());
                verify(userIntegrationService, never()).getInternalUserById(any());
            }
        }

        @Test
        @DisplayName("when security context is missing validateAccess throws before update")
        void whenSecurityContextMissing_throws() {
            UpdateOrderRequest request = OrderTestDataFactory.buildUpdateOrderRequest();
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);

            when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);

            assertThatThrownBy(() -> orderService.updateOrder(OrderTestDataFactory.ORDER_ID, null, request))
                    .isInstanceOf(SecurityContextException.class);

            verify(orderPersistence, never()).updateOrder(any(), any());
            verify(userIntegrationService, never()).getInternalUserById(any());
        }
    }

    @Nested
    @DisplayName("updateOrderStatus")
    class UpdateOrderStatus {

        @Test
        @DisplayName("when transition is allowed updates status via persistence and returns response")
        void whenAllowed_updatesAndReturns() {
            UserResponse user = OrderTestDataFactory.buildUserResponse();
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            OrderResponse out = OrderTestDataFactory.buildOrderResponse(order);
            UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

                when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);
                when(orderPersistence.updateStatus(OrderTestDataFactory.ORDER_ID, OrderStatus.CONFIRMED, order.getVersion()))
                        .thenReturn(true);
                when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
                when(orderMapper.toResponse(order, user)).thenReturn(out);

                OrderResponse result = orderService.updateOrderStatus(
                        OrderTestDataFactory.ORDER_ID,
                        request,
                        OrderTestDataFactory.USER_ID);

                assertThat(result).isEqualTo(out);
                verify(orderPersistence).updateStatus(OrderTestDataFactory.ORDER_ID, OrderStatus.CONFIRMED, order.getVersion());
            }
        }

        @Test
        @DisplayName("when target status equals current skips persistence update")
        void whenSameStatus_skipsUpdate() {
            UserResponse user = OrderTestDataFactory.buildUserResponse();
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.PENDING);
            OrderResponse out = OrderTestDataFactory.buildOrderResponse(order);
            UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.PENDING);

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

                when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);
                when(userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);
                when(orderMapper.toResponse(order, user)).thenReturn(out);

                OrderResponse result = orderService.updateOrderStatus(
                        OrderTestDataFactory.ORDER_ID,
                        request,
                        OrderTestDataFactory.USER_ID);

                assertThat(result).isEqualTo(out);
                verify(orderPersistence, never()).updateStatus(any(), any(), any());
            }
        }

        @Test
        @DisplayName("when transition is not allowed throws InvalidOrderStateException")
        void whenInvalidTransition_throws() {
            Order order = OrderTestDataFactory.buildOrder(OrderStatus.CANCELLED);
            UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);

            try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

                when(orderPersistence.findById(OrderTestDataFactory.ORDER_ID)).thenReturn(order);

                assertThatThrownBy(() -> orderService.updateOrderStatus(
                        OrderTestDataFactory.ORDER_ID,
                        request,
                        OrderTestDataFactory.USER_ID))
                        .isInstanceOf(InvalidOrderStateException.class)
                        .hasMessageContaining("Cannot change status");

                verify(orderPersistence, never()).updateStatus(any(), any(), any());
            }
        }
    }

    @Nested
    @DisplayName("deleteOrder")
    class DeleteOrder {

        @Test
        @DisplayName("delegates to persistence")
        void delegates() {
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
