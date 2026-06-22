package com.bankrupang.sanjijk.payment.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.payment.application.service.PaymentService;
import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.exception.PaymentNotFoundException;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import com.bankrupang.sanjijk.payment.infrastructure.external.TossPaymentsClient;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.request.TossCancelRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundRequestHandler 테스트")
class RefundRequestHandlerTest {

    @InjectMocks
    private RefundRequestHandler refundRequestHandler;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    @Mock
    private PaymentService paymentService;

    @Mock
    private ObjectMapper objectMapper;

    private PaymentOutbox createOutbox(UUID paymentId, int cancelAmount, String cancelReason) throws Exception {
        String payload = String.format(
                "{\"paymentId\":\"%s\",\"cancelAmount\":%d,\"cancelReason\":\"%s\"}",
                paymentId, cancelAmount, cancelReason
        );
        PaymentOutbox outbox = mock(PaymentOutbox.class);
        given(outbox.getPayload()).willReturn(payload);
        given(outbox.getId()).willReturn(UUID.randomUUID());
        given(objectMapper.readValue(eq(payload), eq(java.util.Map.class)))
                .willReturn(java.util.Map.of(
                        "paymentId", paymentId.toString(),
                        "cancelAmount", cancelAmount,
                        "cancelReason", cancelReason
                ));
        return outbox;
    }

    @Nested
    @DisplayName("handle - 환불 처리")
    class Handle {

        @Test
        @DisplayName("정상 환불 - 전액 취소 (cancelAmount = amount → null 전달)")
        void success_full_cancel() throws Exception {
            // given
            UUID paymentId = UUID.randomUUID();
            int amount = 100_000;
            PaymentOutbox outbox = createOutbox(paymentId, amount, "유찰로 인한 보증금 환불");

            Payment payment = mock(Payment.class);
            given(payment.getStatus()).willReturn(PaymentStatus.DONE);
            given(payment.getAmount()).willReturn(amount);
            given(payment.getPaymentKey()).willReturn("paymentKey");
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            // when
            refundRequestHandler.handle(outbox);

            // then - 전액 취소 시 cancelAmount = null
            then(tossPaymentsClient).should().cancel(
                    eq("paymentKey"),
                    eq(new TossCancelRequest("유찰로 인한 보증금 환불", null))
            );
            then(paymentService).should().completeRefund(paymentId, amount, "유찰로 인한 보증금 환불");
        }

        @Test
        @DisplayName("정상 환불 - 부분 취소")
        void success_partial_cancel() throws Exception {
            // given
            UUID paymentId = UUID.randomUUID();
            int totalAmount = 100_000;
            int cancelAmount = 50_000;
            PaymentOutbox outbox = createOutbox(paymentId, cancelAmount, "부분 환불");

            Payment payment = mock(Payment.class);
            given(payment.getStatus()).willReturn(PaymentStatus.DONE);
            given(payment.getAmount()).willReturn(totalAmount);
            given(payment.getPaymentKey()).willReturn("paymentKey");
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            // when
            refundRequestHandler.handle(outbox);

            // then - 부분 취소 시 cancelAmount 그대로 전달
            then(tossPaymentsClient).should().cancel(
                    eq("paymentKey"),
                    eq(new TossCancelRequest("부분 환불", cancelAmount))
            );
            then(paymentService).should().completeRefund(paymentId, cancelAmount, "부분 환불");
        }

        @Test
        @DisplayName("이미 CANCELED - 스킵 (멱등성)")
        void already_canceled_skip() throws Exception {
            // given
            UUID paymentId = UUID.randomUUID();
            PaymentOutbox outbox = createOutbox(paymentId, 100_000, "환불");

            Payment payment = mock(Payment.class);
            given(payment.getStatus()).willReturn(PaymentStatus.CANCELED);
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            // when
            refundRequestHandler.handle(outbox);

            // then
            then(tossPaymentsClient).should(never()).cancel(any(), any());
            then(paymentService).should(never()).completeRefund(any(), anyInt(), any());
        }

        @Test
        @DisplayName("Payment 없음 - 예외 발생")
        void not_found_throws() throws Exception {
            // given
            UUID paymentId = UUID.randomUUID();
            PaymentOutbox outbox = createOutbox(paymentId, 100_000, "환불");
            given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> refundRequestHandler.handle(outbox))
                    .isInstanceOf(RuntimeException.class);
            then(tossPaymentsClient).should(never()).cancel(any(), any());
        }

        @Test
        @DisplayName("Toss cancel API 실패 - 예외 전파")
        void toss_fail_propagates() throws Exception {
            // given
            UUID paymentId = UUID.randomUUID();
            int amount = 100_000;
            PaymentOutbox outbox = createOutbox(paymentId, amount, "환불");

            Payment payment = mock(Payment.class);
            given(payment.getStatus()).willReturn(PaymentStatus.DONE);
            given(payment.getAmount()).willReturn(amount);
            given(payment.getPaymentKey()).willReturn("paymentKey");
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
            given(tossPaymentsClient.cancel(any(), any())).willThrow(new RuntimeException("Toss 오류"));

            // when & then
            assertThatThrownBy(() -> refundRequestHandler.handle(outbox))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("환불 처리 실패");
            then(paymentService).should(never()).completeRefund(any(), anyInt(), any());
        }
    }

    private int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
