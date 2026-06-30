package com.bankrupang.sanjijk.payment.application.service;

import com.bankrupang.sanjijk.payment.application.port.PaymentEventPublisher;
import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentHistory;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import com.bankrupang.sanjijk.payment.domian.exception.PaymentNotFoundException;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentHistoryRepository;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import com.bankrupang.sanjijk.payment.infrastructure.external.PaymentConfirmTransaction;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.response.TossPaymentResponse;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.DepositCreatedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.WinningCreatedEvent;
import com.bankrupang.sanjijk.payment.presentation.dto.request.PaymentConfirmRequest;
import com.bankrupang.sanjijk.payment.presentation.dto.response.PaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 테스트")
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private PaymentConfirmTransaction paymentConfirmTransaction;

    // ================================
    // createDepositPayment
    // ================================

    @Nested
    @DisplayName("createDepositPayment - 보증금 Payment 생성")
    class CreateDepositPayment {

        @Test
        @DisplayName("정상 생성")
        void success() {
            // given
            DepositCreatedEvent event = new DepositCreatedEvent(
                    UUID.randomUUID(), "ORD-12345678", UUID.randomUUID(), UUID.randomUUID(),
                    "테스트 경매", 100_000, LocalDateTime.now().plusHours(2), LocalDateTime.now()
            );
            given(paymentRepository.findByTossOrderId(event.orderNumber()))
                    .willReturn(Optional.empty());

            // when
            paymentService.createDepositPayment(event);

            // then
            then(paymentRepository).should().save(any(Payment.class));
            then(paymentHistoryRepository).should().save(any(PaymentHistory.class));
        }

        @Test
        @DisplayName("중복 이벤트 - 멱등성 처리 (스킵)")
        void duplicate_skip() {
            // given
            DepositCreatedEvent event = new DepositCreatedEvent(
                    UUID.randomUUID(), "ORD-12345678", UUID.randomUUID(), UUID.randomUUID(),
                    "테스트 경매", 100_000, LocalDateTime.now().plusHours(2), LocalDateTime.now()
            );
            Payment existing = mock(Payment.class);
            given(paymentRepository.findByTossOrderId(event.orderNumber()))
                    .willReturn(Optional.of(existing));

            // when
            paymentService.createDepositPayment(event);

            // then
            then(paymentRepository).should(never()).save(any());
        }
    }

    // ================================
    // createWinningPayment
    // ================================

    @Nested
    @DisplayName("createWinningPayment - 낙찰 잔금 Payment 생성")
    class CreateWinningPayment {

        @Test
        @DisplayName("정상 생성 + 낙찰 실패자 환불 요청")
        void success_with_loser_refund() {
            // given
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            WinningCreatedEvent event = new WinningCreatedEvent(
                    UUID.randomUUID(), "ORD-12345678", winnerId, auctionId,
                    "테스트 경매", UUID.randomUUID(), 1_000_000,
                    100_000, 900_000, LocalDateTime.now().plusMinutes(15), LocalDateTime.now()
            );
            given(paymentRepository.findByTossOrderId(event.orderNumber()))
                    .willReturn(Optional.empty());

            Payment loserPayment = mock(Payment.class);
            given(loserPayment.getOrderId()).willReturn(UUID.randomUUID());
            given(loserPayment.getId()).willReturn(UUID.randomUUID());
            given(loserPayment.getAmount()).willReturn(100_000);
            given(loserPayment.getUserId()).willReturn(UUID.randomUUID());
            given(paymentRepository.findByAuctionIdAndPaymentTypeAndStatusAndUserIdNot(
                    auctionId, PaymentType.REPAY, PaymentStatus.DONE, winnerId))
                    .willReturn(List.of(loserPayment));

            // when
            paymentService.createWinningPayment(event);

            // then
            then(paymentRepository).should().save(any(Payment.class));
            then(paymentHistoryRepository).should().save(any(PaymentHistory.class));
            then(paymentEventPublisher).should().publishRefundRequest(any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("낙찰 실패자 없음 - 환불 요청 없음")
        void success_no_losers() {
            // given
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            WinningCreatedEvent event = new WinningCreatedEvent(
                    UUID.randomUUID(), "ORD-12345678", winnerId, auctionId,
                    "테스트 경매", UUID.randomUUID(), 1_000_000,
                    100_000, 900_000, LocalDateTime.now().plusMinutes(15), LocalDateTime.now()
            );
            given(paymentRepository.findByTossOrderId(event.orderNumber()))
                    .willReturn(Optional.empty());
            given(paymentRepository.findByAuctionIdAndPaymentTypeAndStatusAndUserIdNot(
                    any(), any(), any(), any()))
                    .willReturn(List.of());

            // when
            paymentService.createWinningPayment(event);

            // then
            then(paymentEventPublisher).should(never()).publishRefundRequest(any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("중복 이벤트 - 멱등성 처리 (스킵)")
        void duplicate_skip() {
            // given
            WinningCreatedEvent event = new WinningCreatedEvent(
                    UUID.randomUUID(), "ORD-12345678", UUID.randomUUID(), UUID.randomUUID(),
                    "테스트 경매", UUID.randomUUID(), 1_000_000,
                    100_000, 900_000, LocalDateTime.now().plusMinutes(15), LocalDateTime.now()
            );
            given(paymentRepository.findByTossOrderId(event.orderNumber()))
                    .willReturn(Optional.of(mock(Payment.class)));

            // when
            paymentService.createWinningPayment(event);

            // then
            then(paymentRepository).should(never()).save(any());
        }
    }

    // ================================
    // refundAllDeposits
    // ================================

    @Nested
    @DisplayName("refundAllDeposits - 유찰 전체 환불 요청")
    class RefundAllDeposits {

        @Test
        @DisplayName("정상 환불 요청 적재")
        void success() {
            // given
            AuctionFailedEvent event = new AuctionFailedEvent(
                    UUID.randomUUID(), "테스트 경매", UUID.randomUUID(), LocalDateTime.now()
            );
            Payment payment = mock(Payment.class);
            given(payment.getOrderId()).willReturn(UUID.randomUUID());
            given(payment.getId()).willReturn(UUID.randomUUID());
            given(payment.getAmount()).willReturn(100_000);
            given(payment.getUserId()).willReturn(UUID.randomUUID());
            given(paymentRepository.findByAuctionIdAndPaymentTypeAndStatus(
                    event.auctionId(), PaymentType.REPAY, PaymentStatus.DONE))
                    .willReturn(List.of(payment));

            // when
            paymentService.refundAllDeposits(event);

            // then
            then(paymentEventPublisher).should().publishRefundRequest(any(), any(), anyInt(), any());
        }


    }

    // ================================
    // confirmPayment
    // ================================

    @Nested
    @DisplayName("confirmPayment - 결제 승인")
    class ConfirmPayment {

        @Test
        @DisplayName("정상 승인")
        void success() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            PaymentConfirmRequest request = new PaymentConfirmRequest("paymentKey", "tossOrderId", 100_000);
            Payment payment = mock(Payment.class);
            TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);

            given(paymentConfirmTransaction.prepareConfirm(request, userId)).willReturn(paymentId);
            given(paymentConfirmTransaction.callTossConfirm(any(), any(), anyInt())).willReturn(tossResponse);
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
            given(payment.getId()).willReturn(paymentId);
            given(payment.getOrderId()).willReturn(UUID.randomUUID());
            given(payment.getAuctionId()).willReturn(UUID.randomUUID());
            given(payment.getAuctionTitle()).willReturn("테스트 경매");
            given(payment.getPaymentType()).willReturn(PaymentType.NORMAL);
            given(payment.getStatus()).willReturn(PaymentStatus.DONE);
            given(payment.getAmount()).willReturn(100_000);

            // when
            PaymentResponse response = paymentService.confirmPayment(request, userId);

            // then
            then(paymentConfirmTransaction).should().completeConfirm(paymentId, tossResponse, userId);
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Toss API 실패 - failConfirm 호출")
        void toss_fail_calls_failConfirm() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            PaymentConfirmRequest request = new PaymentConfirmRequest("paymentKey", "tossOrderId", 100_000);

            given(paymentConfirmTransaction.prepareConfirm(request, userId)).willReturn(paymentId);
            given(paymentConfirmTransaction.callTossConfirm(any(), any(), anyInt()))
                    .willThrow(new RuntimeException("Toss API 오류"));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPayment(request, userId))
                    .isInstanceOf(RuntimeException.class);
            then(paymentConfirmTransaction).should().failConfirm(eq(paymentId), eq(userId), any(), any());
        }
    }

    // ================================
    // getPayment
    // ================================

    @Nested
    @DisplayName("getPayment - 단건 조회")
    class GetPayment {

        @Test
        @DisplayName("정상 조회")
        void success() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = mock(Payment.class);
            given(payment.getUserId()).willReturn(userId);
            given(payment.getId()).willReturn(paymentId);
            given(payment.getOrderId()).willReturn(UUID.randomUUID());
            given(payment.getAuctionId()).willReturn(UUID.randomUUID());
            given(payment.getAuctionTitle()).willReturn("테스트 경매");
            given(payment.getPaymentType()).willReturn(PaymentType.REPAY);
            given(payment.getStatus()).willReturn(PaymentStatus.DONE);
            given(payment.getAmount()).willReturn(100_000);
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            // when
            PaymentResponse response = paymentService.getPayment(paymentId, userId);

            // then
            assertThat(response.paymentId()).isEqualTo(paymentId);
        }

        @Test
        @DisplayName("타인 결제 조회 - PaymentNotFoundException")
        void unauthorized_throws() {
            // given
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = mock(Payment.class);
            given(payment.getUserId()).willReturn(UUID.randomUUID()); // 다른 사람
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.getPayment(paymentId, userId))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("존재하지 않는 결제 - PaymentNotFoundException")
        void not_found_throws() {
            // given
            UUID paymentId = UUID.randomUUID();
            given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.getPayment(paymentId, UUID.randomUUID()))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    // ================================
    // getPaymentByOrderId
    // 같은 orderId라도 여러 READY 레코드가 존재할 수 있어 List + 최신 1건 조회로 변경됨
    // ================================

    @Nested
    @DisplayName("getPaymentByOrderId - orderId로 결제 조회")
    class GetPaymentByOrderId {

        @Test
        @DisplayName("정상 조회 - 가장 최근 생성된 READY 상태 Payment 반환")
        void success() {
            // given
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = mock(Payment.class);
            given(payment.getUserId()).willReturn(userId);
            given(payment.getId()).willReturn(UUID.randomUUID());
            given(payment.getOrderId()).willReturn(orderId);
            given(payment.getAuctionId()).willReturn(UUID.randomUUID());
            given(payment.getAuctionTitle()).willReturn("테스트 경매");
            given(payment.getPaymentType()).willReturn(PaymentType.NORMAL);
            given(payment.getStatus()).willReturn(PaymentStatus.READY);
            given(payment.getAmount()).willReturn(90);
            given(paymentRepository.findAllByOrderIdAndStatusOrderByCreatedAtDesc(
                    eq(orderId), eq(PaymentStatus.READY), any(Pageable.class)))
                    .willReturn(List.of(payment));

            // when
            PaymentResponse response = paymentService.getPaymentByOrderId(orderId, userId);

            // then
            assertThat(response.orderId()).isEqualTo(orderId);
            assertThat(response.amount()).isEqualTo(90);
        }

        @Test
        @DisplayName("같은 orderId로 여러 READY가 존재해도 최신 1건만 반환 - 예외 없음")
        void multiple_ready_returns_latest_only() {
            // given
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment latest = mock(Payment.class);
            given(latest.getUserId()).willReturn(userId);
            given(latest.getId()).willReturn(UUID.randomUUID());
            given(latest.getOrderId()).willReturn(orderId);
            given(latest.getAuctionId()).willReturn(UUID.randomUUID());
            given(latest.getAuctionTitle()).willReturn("테스트 경매");
            given(latest.getPaymentType()).willReturn(PaymentType.WINNING_REPAY);
            given(latest.getStatus()).willReturn(PaymentStatus.READY);
            given(latest.getAmount()).willReturn(180);
            // Pageable로 limit 1을 걸기 때문에 리포지토리는 항상 최대 1건만 반환한다고 가정
            given(paymentRepository.findAllByOrderIdAndStatusOrderByCreatedAtDesc(
                    eq(orderId), eq(PaymentStatus.READY), any(Pageable.class)))
                    .willReturn(List.of(latest));

            // when
            PaymentResponse response = paymentService.getPaymentByOrderId(orderId, userId);

            // then
            assertThat(response.amount()).isEqualTo(180);
        }

        @Test
        @DisplayName("타인 주문 조회 - PaymentNotFoundException")
        void unauthorized_throws() {
            // given
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment payment = mock(Payment.class);
            given(payment.getUserId()).willReturn(UUID.randomUUID()); // 다른 사람
            given(paymentRepository.findAllByOrderIdAndStatusOrderByCreatedAtDesc(
                    eq(orderId), eq(PaymentStatus.READY), any(Pageable.class)))
                    .willReturn(List.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.getPaymentByOrderId(orderId, userId))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("READY 상태 Payment 없음 - PaymentNotFoundException")
        void not_found_throws() {
            // given
            UUID orderId = UUID.randomUUID();
            given(paymentRepository.findAllByOrderIdAndStatusOrderByCreatedAtDesc(
                    eq(orderId), eq(PaymentStatus.READY), any(Pageable.class)))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> paymentService.getPaymentByOrderId(orderId, UUID.randomUUID()))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    // ================================
    // repayPayment
    // 멱등성 체크(이미 생성된 WINNING_REPAY READY가 있으면 재사용) 케이스 추가
    // ================================

    @Nested
    @DisplayName("repayPayment - 잔금 재결제")
    class RepayPayment {

        @Test
        @DisplayName("정상 재결제 Payment 생성")
        void success() {
            // given
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment abortedPayment = mock(Payment.class);
            given(abortedPayment.getUpdatedAt()).willReturn(LocalDateTime.now().minusMinutes(5));
            given(abortedPayment.getOrderId()).willReturn(orderId);
            given(abortedPayment.getUserId()).willReturn(userId);
            given(abortedPayment.getSellerId()).willReturn(UUID.randomUUID());
            given(abortedPayment.getAuctionId()).willReturn(UUID.randomUUID());
            given(abortedPayment.getAuctionTitle()).willReturn("테스트 경매");
            given(abortedPayment.getAmount()).willReturn(900_000);
            given(abortedPayment.getOriginalAmount()).willReturn(1_000_000);
            given(paymentRepository.findByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.NORMAL, PaymentStatus.READY))
                    .willReturn(Optional.empty());
            given(paymentRepository.findAllByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.WINNING_REPAY, PaymentStatus.READY))
                    .willReturn(List.of());
            given(paymentRepository.findByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.NORMAL, PaymentStatus.ABORTED))
                    .willReturn(Optional.of(abortedPayment));

            // when
            PaymentResponse response = paymentService.repayPayment(orderId, userId);

            // then
            then(paymentRepository).should().save(any(Payment.class));
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("이미 생성된 WINNING_REPAY READY가 있으면 재사용 - 새로 생성하지 않음")
        void already_exists_returns_existing() {
            // given
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Payment existingRepay = mock(Payment.class);
            given(existingRepay.getId()).willReturn(UUID.randomUUID());
            given(existingRepay.getOrderId()).willReturn(orderId);
            given(existingRepay.getAuctionId()).willReturn(UUID.randomUUID());
            given(existingRepay.getAuctionTitle()).willReturn("테스트 경매");
            given(existingRepay.getPaymentType()).willReturn(PaymentType.WINNING_REPAY);
            given(existingRepay.getStatus()).willReturn(PaymentStatus.READY);
            given(existingRepay.getAmount()).willReturn(180);

            given(paymentRepository.findByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.NORMAL, PaymentStatus.READY))
                    .willReturn(Optional.empty());
            given(paymentRepository.findAllByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.WINNING_REPAY, PaymentStatus.READY))
                    .willReturn(List.of(existingRepay));

            // when
            PaymentResponse response = paymentService.repayPayment(orderId, userId);

            // then
            then(paymentRepository).should(never()).save(any());
            assertThat(response.amount()).isEqualTo(180);
        }

        @Test
        @DisplayName("15분 초과 - PaymentNotFoundException")
        void expired_throws() {
            // given
            UUID orderId = UUID.randomUUID();
            Payment abortedPayment = mock(Payment.class);
            given(abortedPayment.getUpdatedAt()).willReturn(LocalDateTime.now().minusMinutes(20));
            given(paymentRepository.findByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.NORMAL, PaymentStatus.READY))
                    .willReturn(Optional.empty());
            given(paymentRepository.findAllByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.WINNING_REPAY, PaymentStatus.READY))
                    .willReturn(List.of());
            given(paymentRepository.findByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.NORMAL, PaymentStatus.ABORTED))
                    .willReturn(Optional.of(abortedPayment));

            // when & then
            assertThatThrownBy(() -> paymentService.repayPayment(orderId, UUID.randomUUID()))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("ABORTED Payment 없음 - PaymentNotFoundException")
        void not_found_throws() {
            // given
            UUID orderId = UUID.randomUUID();
            given(paymentRepository.findByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.NORMAL, PaymentStatus.READY))
                    .willReturn(Optional.empty());
            given(paymentRepository.findAllByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.WINNING_REPAY, PaymentStatus.READY))
                    .willReturn(List.of());
            given(paymentRepository.findByOrderIdAndPaymentTypeAndStatus(
                    orderId, PaymentType.NORMAL, PaymentStatus.ABORTED))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.repayPayment(orderId, UUID.randomUUID()))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }
}
