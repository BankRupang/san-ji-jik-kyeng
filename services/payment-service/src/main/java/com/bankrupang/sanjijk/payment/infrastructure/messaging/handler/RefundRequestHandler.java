package com.bankrupang.sanjijk.payment.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.payment.application.port.PaymentEventPublisher;
import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.domian.exception.PaymentNotFoundException;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import com.bankrupang.sanjijk.payment.infrastructure.external.TossPaymentsClient;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.request.TossCancelRequest;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.RefundCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestHandler {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentEventPublisher paymentEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(PaymentOutbox outbox) {
        try {
            // payload 파싱
            Map<String, Object> payload = objectMapper.readValue(outbox.getPayload(), Map.class);
            UUID paymentId = UUID.fromString((String) payload.get("paymentId"));
            int cancelAmount = (int) payload.get("cancelAmount");
            String cancelReason = (String) payload.get("cancelReason");

            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(PaymentNotFoundException::new);

            log.info("[REFUND] Toss cancel API 호출 시작 - paymentId: {}, paymentKey: {}, cancelAmount: {}",
                    paymentId, payment.getPaymentKey(), cancelAmount);

            // 전액 환불이면 cancelAmount null → Toss가 전액취소로 처리
            Integer tossCancel = (cancelAmount == payment.getAmount()) ? null : cancelAmount;
            tossPaymentsClient.cancel(
                    payment.getPaymentKey(),
                    new TossCancelRequest(cancelReason, tossCancel)
            );

            // Payment 상태 CANCELED로 전이
            payment.refund(cancelAmount, cancelReason);

            // REFUND_COMPLETED 이벤트 Outbox 적재
            paymentEventPublisher.publishRefundCompleted(new RefundCompletedEvent(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getUserId(),
                    payment.getAuctionId(),
                    payment.getAuctionTitle(),
                    cancelAmount,
                    cancelReason,
                    LocalDateTime.now()
            ));

            log.info("[REFUND] 환불 완료 - paymentId: {}, userId: {}, cancelAmount: {}",
                    paymentId, payment.getUserId(), cancelAmount);

        } catch (Exception e) {
            log.error("[REFUND] 환불 처리 실패 - outboxId: {}, error: {}", outbox.getId(), e.getMessage(), e);
            throw new RuntimeException("환불 처리 실패", e);
        }
    }
}
