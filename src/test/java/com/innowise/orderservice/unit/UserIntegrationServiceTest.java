package com.innowise.orderservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.innowise.orderservice.client.UserIntegrationService;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.internal.UserResponse;
import com.innowise.orderservice.exception.notfound.UserNotFoundException;
import com.innowise.orderservice.utils.OrderTestDataFactory;

import feign.FeignException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserIntegrationService (unit tests)")
class UserIntegrationServiceTest {

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private UserIntegrationService userIntegrationService;

    @Nested
    @DisplayName("getInternalUserById")
    class GetInternalUserById {

        @Test
        @DisplayName("delegates to Feign client")
        void returnsUserFromClient() {
            UserResponse user = OrderTestDataFactory.buildUserResponse();
            when(userServiceClient.getInternalUserById(OrderTestDataFactory.USER_ID)).thenReturn(user);

            UserResponse result = userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID);

            assertThat(result).isEqualTo(user);
            verify(userServiceClient).getInternalUserById(OrderTestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("when Feign returns 404 throws UserNotFoundException")
        void whenFeign404_throwsUserNotFound() {
            FeignException fe = mock(FeignException.class);
            when(fe.status()).thenReturn(404);
            when(userServiceClient.getInternalUserById(OrderTestDataFactory.USER_ID)).thenThrow(fe);

            assertThatThrownBy(() -> userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(OrderTestDataFactory.USER_ID.toString());
        }

        @Test
        @DisplayName("when Feign returns non-404 error rethrows FeignException")
        void whenFeignOtherStatus_rethrows() {
            FeignException fe = mock(FeignException.class);
            when(fe.status()).thenReturn(503);
            when(userServiceClient.getInternalUserById(OrderTestDataFactory.USER_ID)).thenThrow(fe);

            assertThatThrownBy(() -> userIntegrationService.getInternalUserById(OrderTestDataFactory.USER_ID))
                    .isSameAs(fe);
        }
    }

    @Nested
    @DisplayName("getInternalUsersByIds")
    class GetInternalUsersByIds {

        @Test
        @DisplayName("delegates to Feign client")
        void returnsListFromClient() {
            List<UUID> ids = List.of(OrderTestDataFactory.USER_ID);
            List<UserResponse> users = List.of(OrderTestDataFactory.buildUserResponse());
            when(userServiceClient.getInternalUsersByIds(ids)).thenReturn(users);

            List<UserResponse> result = userIntegrationService.getInternalUsersByIds(ids);

            assertThat(result).isEqualTo(users);
            verify(userServiceClient).getInternalUsersByIds(ids);
        }

        @Test
        @DisplayName("when Feign returns 404 throws UserNotFoundException")
        void whenFeign404_throwsUserNotFound() {
            List<UUID> ids = List.of(OrderTestDataFactory.USER_ID);
            FeignException fe = mock(FeignException.class);
            when(fe.status()).thenReturn(404);
            when(userServiceClient.getInternalUsersByIds(ids)).thenThrow(fe);

            assertThatThrownBy(() -> userIntegrationService.getInternalUsersByIds(ids))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("when Feign returns non-404 error rethrows FeignException")
        void whenFeignOtherStatus_rethrows() {
            List<UUID> ids = List.of(OrderTestDataFactory.USER_ID);
            FeignException fe = mock(FeignException.class);
            when(fe.status()).thenReturn(500);
            when(userServiceClient.getInternalUsersByIds(ids)).thenThrow(fe);

            assertThatThrownBy(() -> userIntegrationService.getInternalUsersByIds(ids))
                    .isSameAs(fe);
        }
    }
}
