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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestHandler {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public void handle(PaymentOutbox outbox) {
        try {
            Map<String, Object> payload = objectMapper.readValue(outbox.getPayload(), Map.class);
            UUID paymentId = UUID.fromString((String) payload.get("paymentId"));
            int cancelAmount = ((Number) payload.get("cancelAmount")).intValue();
            String cancelReason = (String) payload.get("cancelReason");

            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(PaymentNotFoundException::new);

            // 멱등성: 재시도 시 이미 취소된 경우 방지
            if (payment.getStatus() == PaymentStatus.CANCELED) {
                log.warn("[REFUND] 이미 취소된 결제 - paymentId: {}", paymentId);
                return;
            }

            log.info("[REFUND] Toss cancel API 호출 시작 - paymentId: {}, cancelAmount: {}",
                    paymentId, cancelAmount);

            // Toss cancel API 호출 (트랜잭션 밖 - DB 커넥션 점유 방지)
            Integer tossCancel = (cancelAmount == payment.getAmount()) ? null : cancelAmount;
            tossPaymentsClient.cancel(payment.getPaymentKey(), new TossCancelRequest(cancelReason, tossCancel));

            // DB 업데이트 별도 @Transactional
            paymentService.completeRefund(paymentId, cancelAmount, cancelReason);

            log.info("[REFUND] 환불 완료 - paymentId: {}, cancelAmount: {}", paymentId, cancelAmount);

        } catch (Exception e) {
            log.error("[REFUND] 환불 처리 실패 - outboxId: {}, error: {}", outbox.getId(), e.getMessage(), e);
            throw new RuntimeException("환불 처리 실패", e);
        }
    }
}
