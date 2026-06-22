package com.bankrupang.sanjijk.payment.domian.entity;

import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import com.bankrupang.sanjijk.payment.domian.exception.InvalidPaymentStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();
    private final UUID auctionId = UUID.randomUUID();

    // ──────────────────────────────────────────
    // NORMAL 결제 (낙찰 결제)
    // ──────────────────────────────────────────
    @Nested
    @DisplayName("NORMAL 결제 상태 전이")
    class NormalPaymentTest {

        private Payment payment;

        @BeforeEach
        void setUp() {
            payment = Payment.create(
                    orderId, userId, sellerId, auctionId,
                    "참외 10박스", "tossOrder-001",
                    PaymentType.NORMAL, 500000, null
            );
        }

        @Test
        @DisplayName("초기 상태는 READY")
        void initialStatus() {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        }

        @Test
        @DisplayName("READY → IN_PROGRESS")
        void inProgress() {
            payment.inProgress();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("IN_PROGRESS → DONE")
        void confirm() {
            payment.inProgress();
            payment.confirm("paymentKey-001", "11", "1234-****-****-5678", "신용", 0, "https://receipt.url", LocalDateTime.now());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
            assertThat(payment.getPaymentKey()).isEqualTo("paymentKey-001");
        }

        @Test
        @DisplayName("READY → ABORTED")
        void failFromReady() {
            payment.fail("REJECT_CARD", "카드 잔액 부족");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ABORTED);
            assertThat(payment.getFailureCode()).isEqualTo("REJECT_CARD");
        }

        @Test
        @DisplayName("IN_PROGRESS → ABORTED")
        void failFromInProgress() {
            payment.inProgress();
            payment.fail("REJECT_CARD", "카드 잔액 부족");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ABORTED);
        }

        @Test
        @DisplayName("READY → EXPIRED")
        void expire() {
            payment.expire();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        }

        @Test
        @DisplayName("READY 아닌 상태에서 inProgress() 시도 시 예외")
        void inProgress_invalidStatus() {
            payment.inProgress();
            assertThatThrownBy(payment::inProgress)
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }

        @Test
        @DisplayName("IN_PROGRESS 아닌 상태에서 confirm() 시도 시 예외")
        void confirm_invalidStatus() {
            assertThatThrownBy(() ->
                    payment.confirm("paymentKey-001", "11", "1234-****-****-5678", "신용", 0, "https://receipt.url", LocalDateTime.now())
            ).isInstanceOf(InvalidPaymentStatusException.class);
        }

        @Test
        @DisplayName("DONE 상태에서 fail() 시도 시 예외")
        void fail_invalidStatus() {
            payment.inProgress();
            payment.confirm("paymentKey-001", "11", "1234-****-****-5678", "신용", 0, "https://receipt.url", LocalDateTime.now());
            assertThatThrownBy(() -> payment.fail("REJECT_CARD", "카드 잔액 부족"))
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }

        @Test
        @DisplayName("READY 아닌 상태에서 expire() 시도 시 예외")
        void expire_invalidStatus() {
            payment.inProgress();
            assertThatThrownBy(payment::expire)
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }
    }

    // ──────────────────────────────────────────
    // REPAY 결제 (보증금 재결제 - 위약금)
    // ──────────────────────────────────────────
    @Nested
    @DisplayName("REPAY 결제 상태 전이")
    class RepayPaymentTest {

        private Payment payment;

        @BeforeEach
        void setUp() {
            payment = Payment.create(
                    orderId, userId, null, auctionId,
                    "참외 10박스", "tossOrder-002",
                    PaymentType.REPAY, 50000, 500000
            );
        }

        @Test
        @DisplayName("초기 상태는 READY")
        void initialStatus() {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        }

        @Test
        @DisplayName("READY → IN_PROGRESS → DONE")
        void confirmRepay() {
            payment.inProgress();
            payment.confirm("paymentKey-002", "11", "1234-****-****-5678", "신용", 0, "https://receipt.url", LocalDateTime.now());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        }

        @Test
        @DisplayName("READY → ABORTED")
        void failRepay() {
            payment.fail("REJECT_CARD", "카드 잔액 부족");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ABORTED);
        }
    }
}