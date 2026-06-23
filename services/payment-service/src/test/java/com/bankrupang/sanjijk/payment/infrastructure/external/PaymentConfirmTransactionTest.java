package com.bankrupang.sanjijk.payment.infrastructure.external;

import com.bankrupang.sanjijk.payment.application.port.PaymentEventPublisher;
import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentHistory;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import com.bankrupang.sanjijk.payment.domian.exception.PaymentAmountMismatchException;
import com.bankrupang.sanjijk.payment.domian.exception.PaymentNotFoundException;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentHistoryRepository;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.request.TossConfirmRequest;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.response.TossPaymentResponse;
import com.bankrupang.sanjijk.payment.presentation.dto.request.PaymentConfirmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentConfirmTransaction 테스트")
class PaymentConfirmTransactionTest {

    @InjectMocks
    private PaymentConfirmTransaction paymentConfirmTransaction;

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    // ================================
    // prepareConfirm
    // ================================

    @Nested
    @DisplayName("prepareConfirm - READY → IN_PROGRESS")
    class PrepareConfirm {

        @Test
        @DisplayName("정상 - paymentId 반환")
        void success() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            PaymentConfirmRequest request = new PaymentConfirmRequest("paymentKey", "tossOrderId", 100_000);
            Payment payment = mock(Payment.class);
            given(payment.getId()).willReturn(paymentId);
            given(payment.getAmount()).willReturn(100_000);
            given(payment.getOrderId()).willReturn(UUID.randomUUID());
            given(payment.getPaymentType()).willReturn(PaymentType.REPAY);
            given(paymentRepository.findByTossOrderId("tossOrderId")).willReturn(Optional.of(payment));

            // when
            UUID result = paymentConfirmTransaction.prepareConfirm(request, userId);

            // then
            then(payment).should().inProgress();
            then(paymentHistoryRepository).should().save(any(PaymentHistory.class));
            org.assertj.core.api.Assertions.assertThat(result).isEqualTo(paymentId);
        }

        @Test
        @DisplayName("금액 불일치 - PaymentAmountMismatchException")
        void amount_mismatch_throws() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest("paymentKey", "tossOrderId", 100_000);
            Payment payment = mock(Payment.class);
            given(payment.getAmount()).willReturn(200_000); // 다른 금액
            given(paymentRepository.findByTossOrderId("tossOrderId")).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentConfirmTransaction.prepareConfirm(request, UUID.randomUUID()))
                    .isInstanceOf(PaymentAmountMismatchException.class);
        }

        @Test
        @DisplayName("Payment 없음 - PaymentNotFoundException")
        void not_found_throws() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest("paymentKey", "tossOrderId", 100_000);
            given(paymentRepository.findByTossOrderId("tossOrderId")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentConfirmTransaction.prepareConfirm(request, UUID.randomUUID()))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    // ================================
    // completeConfirm
    // ================================

    @Nested
    @DisplayName("completeConfirm - 결제 성공 처리")
    class CompleteConfirm {

        @Test
        @DisplayName("NORMAL 타입 - Redis write 없음")
        void normal_no_redis() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = mock(Payment.class);
            given(payment.getPaymentType()).willReturn(PaymentType.NORMAL);
            given(payment.getId()).willReturn(paymentId);
            given(payment.getOrderId()).willReturn(UUID.randomUUID());
            given(payment.getAuctionId()).willReturn(UUID.randomUUID());
            given(payment.getUserId()).willReturn(UUID.randomUUID());
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);
            given(tossResponse.paymentKey()).willReturn("paymentKey");
            given(tossResponse.approvedAt()).willReturn("2024-01-01T00:00:00+09:00");

            // when
            paymentConfirmTransaction.completeConfirm(paymentId, tossResponse, userId);

            // then
            then(redisTemplate).shouldHaveNoInteractions();
            then(paymentEventPublisher).should().publishPaymentCompleted(any());
        }

        @Test
        @DisplayName("REPAY 타입 + endAt 있음 - Redis write")
        void repay_with_endAt_writes_redis() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = mock(Payment.class);
            given(payment.getPaymentType()).willReturn(PaymentType.REPAY);
            given(payment.getEndAt()).willReturn(LocalDateTime.now().plusHours(2));
            given(payment.getAuctionId()).willReturn(UUID.randomUUID());
            given(payment.getUserId()).willReturn(UUID.randomUUID());
            given(payment.getId()).willReturn(paymentId);
            given(payment.getOrderId()).willReturn(UUID.randomUUID());
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            given(redisTemplate.opsForValue()).willReturn(valueOps);

            TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);
            given(tossResponse.paymentKey()).willReturn("paymentKey");
            given(tossResponse.approvedAt()).willReturn("2024-01-01T00:00:00+09:00");

            // when
            paymentConfirmTransaction.completeConfirm(paymentId, tossResponse, userId);

            // then
            then(valueOps).should().set(any(), any(), any());
            then(paymentEventPublisher).should().publishPaymentCompleted(any());
        }

        @Test
        @DisplayName("REPAY 타입 + endAt null - Redis write 생략")
        void repay_without_endAt_skips_redis() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = mock(Payment.class);
            given(payment.getPaymentType()).willReturn(PaymentType.REPAY);
            given(payment.getEndAt()).willReturn(null);
            given(payment.getId()).willReturn(paymentId);
            given(payment.getOrderId()).willReturn(UUID.randomUUID());
            given(payment.getAuctionId()).willReturn(UUID.randomUUID());
            given(payment.getUserId()).willReturn(UUID.randomUUID());
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);
            given(tossResponse.paymentKey()).willReturn("paymentKey");
            given(tossResponse.approvedAt()).willReturn("2024-01-01T00:00:00+09:00");

            // when
            paymentConfirmTransaction.completeConfirm(paymentId, tossResponse, userId);

            // then
            then(redisTemplate).shouldHaveNoInteractions();
        }
    }

    // ================================
    // failConfirm
    // ================================

    @Nested
    @DisplayName("failConfirm - 결제 실패 처리")
    class FailConfirm {

        @Test
        @DisplayName("정상 - ABORTED + Outbox 적재")
        void success() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = mock(Payment.class);
            given(payment.getId()).willReturn(paymentId);
            given(payment.getOrderId()).willReturn(UUID.randomUUID());
            given(payment.getAuctionId()).willReturn(UUID.randomUUID());
            given(payment.getUserId()).willReturn(UUID.randomUUID());
            given(payment.getPaymentType()).willReturn(PaymentType.NORMAL);
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            // when
            paymentConfirmTransaction.failConfirm(paymentId, userId, "REJECT", "잔액 부족");

            // then
            then(payment).should().fail("REJECT", "잔액 부족");
            then(paymentHistoryRepository).should().save(any(PaymentHistory.class));
            then(paymentEventPublisher).should().publishPaymentFailed(any());
        }

        @Test
        @DisplayName("Payment 없음 - PaymentNotFoundException")
        void not_found_throws() {
            // given
            UUID paymentId = UUID.randomUUID();
            given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    paymentConfirmTransaction.failConfirm(paymentId, UUID.randomUUID(), "REJECT", "오류"))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    // ================================
    // callTossConfirm
    // ================================

    @Nested
    @DisplayName("callTossConfirm - Toss API 호출")
    class CallTossConfirm {

        @Test
        @DisplayName("정상 호출")
        void success() {
            // given
            TossPaymentResponse response = mock(TossPaymentResponse.class);
            given(tossPaymentsClient.confirm(any(TossConfirmRequest.class))).willReturn(response);

            // when
            TossPaymentResponse result = paymentConfirmTransaction.callTossConfirm(
                    "paymentKey", "tossOrderId", 100_000);

            // then
            then(tossPaymentsClient).should().confirm(any(TossConfirmRequest.class));
            org.assertj.core.api.Assertions.assertThat(result).isEqualTo(response);
        }
    }
}
