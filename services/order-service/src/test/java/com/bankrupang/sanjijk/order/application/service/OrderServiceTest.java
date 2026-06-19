package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.application.port.OrderEventPublisher;
import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import com.bankrupang.sanjijk.order.presentation.dto.request.OrderDepositCreateRequest;
import com.bankrupang.sanjijk.order.presentation.dto.response.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("OrderService 테스트")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Nested
    @DisplayName("createDepositOrder()")
    class createDepositOrder {

        private UUID userId;
        private OrderDepositCreateRequest request;

        @BeforeEach
        void setUp() {
            userId = UUID.randomUUID();
            request = new OrderDepositCreateRequest(
                    UUID.randomUUID(),
                    "싱싱한 광어",
                    10000L,
                    "사람",
                    "UI123456"
            );
        }

        @Test
        @DisplayName(("T-ORD-DEP-01: 보증금 주문 정상 생성"))
        void createOrder() {
            // given
            given(orderRepository.findByUserIdAndAuctionIdAndOrderType(
                    userId, request.auctionId(), OrderType.DEPOSIT))
                    .willReturn(Optional.empty());

            //when
            OrderResponse response = orderService.createDepositOrder(userId, request);

            // then
            assertThat(response.orderType()).isEqualTo(OrderType.DEPOSIT);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.auctionId()).isEqualTo(request.auctionId());
            assertThat(response.amount()).isEqualTo(10000L);

            verify(orderRepository).save(any(Order.class));
            verify(orderEventPublisher).publishDepositCreated(any(Order.class));
        }

        @Test
        @DisplayName("T-ORD-DEP-02: 중복 보증금 주문 시 예외 발생")
        void createDepositOrderDuplicate(){
            // given
            given(orderRepository.findByUserIdAndAuctionIdAndOrderType(
                    userId, request.auctionId(), OrderType.DEPOSIT))
                    .willReturn(Optional.of(mock(Order.class)));

           // when + then
            assertThatThrownBy(() -> orderService.createDepositOrder(userId, request))
                    .isInstanceOf(RuntimeException.class);
            verify(orderRepository, never()).save(any(Order.class));
            verify(orderEventPublisher, never()).publishDepositCreated(any(Order.class));

        }

    }
}