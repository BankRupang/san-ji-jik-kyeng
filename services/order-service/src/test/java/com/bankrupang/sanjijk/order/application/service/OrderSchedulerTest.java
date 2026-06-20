package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.application.service.OrderScheduler;
import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("OrderScheduler 테스트")
@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {

    @InjectMocks
    private OrderScheduler orderScheduler;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    @Lazy
    private OrderScheduler self;

    @Nested
    @DisplayName("expireUnpaidOne()")
    class expireUnpaidOne {

        @Test
        @DisplayName("T-PAY-WIN-04: 결제 기한 초과 → PENALTY_PENDING 전환")
        void expireUnpaidWinningOrder() {
            // given
            Order winningOrder = Order.createWinningOrder(
                    UUID.randomUUID(), UUID.randomUUID(),
                    "사람", "UI123456",
                    UUID.randomUUID(), "싱싱한 광어",
                    50000, null);

            // when
            orderScheduler.expireUnpaidOne(winningOrder);

            // then
            assertThat(winningOrder.getStatus()).isEqualTo(OrderStatus.PENALTY_PENDING);
        }
    }

    @Nested
    @DisplayName("expirePenaltyOne()")
    class expirePenaltyOne {

        @Test
        @DisplayName("T-PEN-01: 패널티 기한 초과 → EXPIRED + DEPOSIT FORFEITED")
        void expirePenaltyOrder() {
            // given
            UUID userId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();

            Order winningOrder = Order.createWinningOrder(
                    userId, UUID.randomUUID(),
                    "사람", "UI123456",
                    auctionId, "싱싱한 광어",
                    50000, null);
            winningOrder.markPaymentFailed();
            winningOrder.markPenaltyPending();

            Order depositOrder = Order.createDepositOrder(
                    userId, "사람", "UI123456",
                    auctionId, "싱싱한 광어", 10000);
            depositOrder.markPaymentSuccess();

            given(orderRepository.findByUserIdAndAuctionIdAndOrderType(
                    userId, auctionId, OrderType.DEPOSIT))
                    .willReturn(Optional.of(depositOrder));

            // when
            orderScheduler.expirePenaltyOne(winningOrder);

            // then
            assertThat(winningOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(depositOrder.getStatus()).isEqualTo(OrderStatus.FORFEITED);
        }

        @Test
        @DisplayName("DEPOSIT 주문 없을 시 WINNING만 EXPIRED 전환")
        void expirePenaltyOrderWithoutDeposit() {
            // given
            UUID userId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();

            Order winningOrder = Order.createWinningOrder(
                    userId, UUID.randomUUID(),
                    "사람", "UI123456",
                    auctionId, "싱싱한 광어",
                    50000, null);
            winningOrder.markPaymentFailed();
            winningOrder.markPenaltyPending();

            given(orderRepository.findByUserIdAndAuctionIdAndOrderType(
                    userId, auctionId, OrderType.DEPOSIT))
                    .willReturn(Optional.empty());

            // when
            orderScheduler.expirePenaltyOne(winningOrder);

            // then
            assertThat(winningOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        }
    }
}