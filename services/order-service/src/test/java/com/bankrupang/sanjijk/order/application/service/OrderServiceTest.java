package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.application.port.OrderEventPublisher;
import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import com.bankrupang.sanjijk.order.infrastructure.feign.dto.UserInfoResponse;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.*;
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

import java.time.LocalDateTime;
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
    private OrderEventService orderEventService;

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
                    10000,
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
            assertThat(response.amount()).isEqualTo(10000);

            verify(orderRepository).save(any(Order.class));
            verify(orderEventService).publishDepositCreated(any(Order.class));
        }

        @Test
        @DisplayName("T-ORD-DEP-02: 중복 보증금 주문 시 예외 발생")
        void createDepositOrderDuplicate() {
            // given
            given(orderRepository.findByUserIdAndAuctionIdAndOrderType(
                    userId, request.auctionId(), OrderType.DEPOSIT))
                    .willReturn(Optional.of(mock(Order.class)));

            // when + then
            assertThatThrownBy(() -> orderService.createDepositOrder(userId, request))
                    .isInstanceOf(RuntimeException.class);
            verify(orderRepository, never()).save(any(Order.class));
            verify(orderEventService, never()).publishDepositCreated(any(Order.class));

        }
    }

        @Nested
        @DisplayName("createWinningOrder()")
        class createWinningOrder {

            private AuctionWonEvent event;
            private UserInfoResponse userInfo;

            @BeforeEach
            void setUp() {
                event = new AuctionWonEvent(
                        UUID.randomUUID(),
                        "싱싱한 광어",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        50000,
                        10000,
                        LocalDateTime.now()
                );
                userInfo = new UserInfoResponse(event.winnerId(), "사람", "UI123456");
            }

            @Test
            @DisplayName("T-ORD-WIN-01: 낙찰 주문 정상 생성")
            void createWinningOrder() {
                // given- 멱등성 확인은 AuctionWonHandler에서 existsWinningOrder()로 확인

                // when
                orderService.createWinningOrder(event, userInfo);

                // then
                verify(orderRepository).save(any(Order.class));
                verify(orderEventService).publishWinningCreated(any(Order.class), eq(event.depositAmount()));
            }

            @Test
            @DisplayName("T-ORD-WIN-03: 낙찰 이벤트 중복 수신 멱등성")
            void createWinningOrderDuplicate() {
                // given
                given(orderRepository.findByUserIdAndAuctionIdAndOrderType(
                        event.winnerId(), event.auctionId(), OrderType.WINNING))
                        .willReturn(Optional.of(mock(Order.class)));

                // when - AuctionWonHandler에서 existsWinningOrder 체크 후 스킵
                boolean exists = orderService.existsWinningOrder(event.auctionId(), event.winnerId());

                // then
                assertThat(exists).isTrue();
                verify(orderRepository, never()).save(any(Order.class));
            }
        }

        @Nested
        @DisplayName("completePayment()")
        class completePayment {

            private Order depositOrder;
            private Order winningOrder;

            @BeforeEach
            void setUp() {
                depositOrder = Order.createDepositOrder(
                        UUID.randomUUID(), "사람", "UI123456",
                        UUID.randomUUID(), "싱싱한 광어", 10000);

                winningOrder = Order.createWinningOrder(
                        UUID.randomUUID(), UUID.randomUUID(),
                        "사람", "UI123456",
                        UUID.randomUUID(), "싱싱한 광어",
                        50000, null);
            }

            @Test
            @DisplayName("T-PAY-DEP-01: 예치금 결제 완료 → PAYMENT_SUCCESS")
            void completeDepositPayment() {
                // given
                PaymentCompletedEvent event = new PaymentCompletedEvent(
                        depositOrder.getId(), UUID.randomUUID(), "싱싱한 광어",
                        depositOrder.getUserId(), UUID.randomUUID(),
                        10000, 10000, "NORMAL", LocalDateTime.now());

                given(orderRepository.findById(event.orderId())).willReturn(Optional.of(depositOrder));

                // when
                orderService.completePayment(event);

                // then
                assertThat(depositOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_SUCCESS);
            }

            @Test
            @DisplayName("T-PAY-WIN-01: 낙찰 결제 완료 → COMPLETED")
            void completeWinningPayment() {
                // given
                PaymentCompletedEvent event = new PaymentCompletedEvent(
                        winningOrder.getId(), UUID.randomUUID(), "싱싱한 광어",
                        winningOrder.getUserId(), UUID.randomUUID(),
                        50000, 40000, "NORMAL", LocalDateTime.now());

                given(orderRepository.findById(event.orderId())).willReturn(Optional.of(winningOrder));

                // when
                orderService.completePayment(event);

                // then
                assertThat(winningOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            }

            @Test
            @DisplayName("T-PEN-02: 재결제 성공 → COMPLETED")
            void completePenaltyPayment() {
                // given
                winningOrder.markPaymentFailed();
                winningOrder.markPenaltyPending();

                PaymentCompletedEvent event = new PaymentCompletedEvent(
                        winningOrder.getId(), UUID.randomUUID(), "싱싱한 광어",
                        winningOrder.getUserId(), UUID.randomUUID(),
                        50000, 40000, "REPAY", LocalDateTime.now());

                given(orderRepository.findById(event.orderId())).willReturn(Optional.of(winningOrder));

                // when
                orderService.completePayment(event);

                // then
                assertThat(winningOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            }
        }

        @Nested
        @DisplayName("failPayment()")
        class failPayment {

            private Order winningOrder;

            @BeforeEach
            void setUp() {
                winningOrder = Order.createWinningOrder(
                        UUID.randomUUID(), UUID.randomUUID(),
                        "사람", "UI123456",
                        UUID.randomUUID(), "싱싱한 광어",
                        50000, null);
            }

            @Test
            @DisplayName("T-PAY-WIN-03: 결제 실패 → PENALTY_PENDING")
            void failWinningPayment() {
                // given
                PaymentFailedEvent event = new PaymentFailedEvent(
                        winningOrder.getId(), UUID.randomUUID(), "싱싱한 광어",
                        winningOrder.getUserId(), UUID.randomUUID(),
                        50000, "잔액 부족", LocalDateTime.now());

                given(orderRepository.findById(event.orderId())).willReturn(Optional.of(winningOrder));

                // when
                orderService.failPayment(event);

                // then
                assertThat(winningOrder.getStatus()).isEqualTo(OrderStatus.PENALTY_PENDING);
            }
        }

        @Nested
        @DisplayName("forfeitDeposit()")
        class forfeitDeposit {

            private Order depositOrder;

            @BeforeEach
            void setUp() {
                depositOrder = Order.createDepositOrder(
                        UUID.randomUUID(), "사람", "UI123456",
                        UUID.randomUUID(), "싱싱한 광어", 10000);
                depositOrder.markPaymentSuccess();
            }

            @Test
            @DisplayName("T-PEN-01: 예치금 몰수 → FORFEITED")
            void forfeitDepositOrder() {
                // given
                DepositForfeitedEvent event = new DepositForfeitedEvent(
                        depositOrder.getId(), UUID.randomUUID(), "싱싱한 광어",
                        depositOrder.getUserId(), UUID.randomUUID(),
                        10000, LocalDateTime.now());

                given(orderRepository.findById(event.orderId())).willReturn(Optional.of(depositOrder));

                // when
                orderService.forfeitDeposit(event);

                // then
                assertThat(depositOrder.getStatus()).isEqualTo(OrderStatus.FORFEITED);
            }
        }

        @Nested
        @DisplayName("completeRefund()")
        class completeRefund {

            private Order depositOrder;

            @BeforeEach
            void setUp() {
                depositOrder = Order.createDepositOrder(
                        UUID.randomUUID(), "사람", "UI123456",
                        UUID.randomUUID(), "싱싱한 광어", 10000);
                depositOrder.markPaymentSuccess();
            }

            @Test
            @DisplayName("T-PAY-DEP-03: 예치금 환불 완료 → REFUNDED")
            void completeDepositRefund() {
                // given
                RefundCompletedEvent event = new RefundCompletedEvent(
                        depositOrder.getId(), UUID.randomUUID(), "싱싱한 광어",
                        depositOrder.getUserId(), UUID.randomUUID(),
                        10000, LocalDateTime.now());

                given(orderRepository.findById(event.orderId())).willReturn(Optional.of(depositOrder));

                // when
                orderService.completeRefund(event);

                // then
                assertThat(depositOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            }
        }

    }