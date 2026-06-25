package com.bankrupang.sanjijk.order.domain.entity;

import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.exception.InvalidOrderStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();
    private final UUID auctionId = UUID.randomUUID();

    // ──────────────────────────────────────────
    // DEPOSIT 주문
    // ──────────────────────────────────────────
    @Nested
    @DisplayName("DEPOSIT 주문 상태 전이")
    class DepositOrderTest {

        private Order order;

        @BeforeEach
        void setUp() {
            order = Order.createDepositOrder(userId, "김지민", "U123456", auctionId, "참외 10박스", 50000);
        }

        @Test
        @DisplayName("PENDING → PAYMENT_SUCCESS")
        void markPaymentSuccess() {
            order.markPaymentSuccess();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_SUCCESS);
        }

        @Test
        @DisplayName("PAYMENT_SUCCESS → REFUNDED")
        void markRefunded() {
            order.markPaymentSuccess();
            order.markRefunded();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        }

        @Test
        @DisplayName("PAYMENT_SUCCESS → FORFEITED")
        void markForfeited() {
            order.markPaymentSuccess();
            order.markForfeited();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FORFEITED);
        }

        @Test
        @DisplayName("PENDING 아닌 상태에서 markPaymentSuccess() 시도 시 예외")
        void markPaymentSuccess_invalidStatus() {
            order.markPaymentSuccess();
            assertThatThrownBy(order::markPaymentSuccess)
                    .isInstanceOf(InvalidOrderStatusException.class);
        }

        @Test
        @DisplayName("PAYMENT_SUCCESS 아닌 상태에서 markRefunded() 시도 시 예외")
        void markRefunded_invalidStatus() {
            assertThatThrownBy(order::markRefunded)
                    .isInstanceOf(InvalidOrderStatusException.class);
        }

        @Test
        @DisplayName("PAYMENT_SUCCESS 아닌 상태에서 markForfeited() 시도 시 예외")
        void markForfeited_invalidStatus() {
            assertThatThrownBy(order::markForfeited)
                    .isInstanceOf(InvalidOrderStatusException.class);
        }
    }

    // ──────────────────────────────────────────
    // WINNING 주문
    // ──────────────────────────────────────────
    @Nested
    @DisplayName("WINNING 주문 상태 전이")
    class WinningOrderTest {

        private Order order;

        @BeforeEach
        void setUp() {
            order = Order.createWinningOrder(userId, sellerId , "김지민", "U123456", auctionId, "참외 10박스",500000, "문 앞에 놔주세요");
        }

        @Test
        @DisplayName("PENDING → PAYMENT_SUCCESS")
        void markPaymentSuccess() {
            order.markPaymentSuccess();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_SUCCESS);
        }

        @Test
        @DisplayName("PAYMENT_SUCCESS → COMPLETED")
        void markCompleted() {
            order.markPaymentSuccess();
            order.markCompleted();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("PENDING → PAYMENT_FAILED, penaltyDueAt 세팅 확인")
        void markPaymentFailed() {
            order.markPaymentFailed();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            assertThat(order.getPenaltyDueAt()).isNotNull();
        }

        @Test
        @DisplayName("PAYMENT_FAILED → PENALTY_PENDING")
        void markPenaltyPending() {
            order.markPaymentFailed();
            order.markPenaltyPending();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENALTY_PENDING);
        }

        @Test
        @DisplayName("PENALTY_PENDING → COMPLETED (재결제 성공)")
        void markPenaltyCompleted() {
            order.markPaymentFailed();
            order.markPenaltyPending();
            order.markPenaltyCompleted();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("PENALTY_PENDING → EXPIRED (15분 초과)")
        void markExpired() {
            order.markPaymentFailed();
            order.markPenaltyPending();
            order.markExpired();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        }

        @Test
        @DisplayName("PAYMENT_SUCCESS 아닌 상태에서 markCompleted() 시도 시 예외")
        void markCompleted_invalidStatus() {
            assertThatThrownBy(order::markCompleted)
                    .isInstanceOf(InvalidOrderStatusException.class);
        }

        @Test
        @DisplayName("PENDING 아닌 상태에서 markPaymentFailed() 시도 시 예외")
        void markPaymentFailed_invalidStatus() {
            order.markPaymentSuccess();
            assertThatThrownBy(order::markPaymentFailed)
                    .isInstanceOf(InvalidOrderStatusException.class);
        }

        @Test
        @DisplayName("PAYMENT_FAILED 아닌 상태에서 markPenaltyPending() 시도 시 예외")
        void markPenaltyPending_invalidStatus() {
            assertThatThrownBy(order::markPenaltyPending)
                    .isInstanceOf(InvalidOrderStatusException.class);
        }

        @Test
        @DisplayName("PENALTY_PENDING 아닌 상태에서 markPenaltyCompleted() 시도 시 예외")
        void markPenaltyCompleted_invalidStatus() {
            order.markPaymentFailed();
            assertThatThrownBy(order::markPenaltyCompleted)
                    .isInstanceOf(InvalidOrderStatusException.class);
        }

        @Test
        @DisplayName("PENALTY_PENDING 아닌 상태에서 markExpired() 시도 시 예외")
        void markExpired_invalidStatus() {
            order.markPaymentFailed();
            assertThatThrownBy(order::markExpired)
                    .isInstanceOf(InvalidOrderStatusException.class);
        }
    }
}