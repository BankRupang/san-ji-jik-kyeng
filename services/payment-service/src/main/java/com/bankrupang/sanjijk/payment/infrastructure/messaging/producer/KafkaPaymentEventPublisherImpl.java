package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.payment.application.port.PaymentEventPublisher;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.DepositForfeitedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.PaymentCompletedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.PaymentFailedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.RefundCompletedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.outbox.PaymentOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisherImpl implements PaymentEventPublisher {

    private final PaymentOutboxJpaRepository paymentOutboxJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        saveOutbox("PAYMENT", event.orderId(), "PAYMENT_COMPLETED", event);
    }

    @Override
    public void publishPaymentFailed(PaymentFailedEvent event) {
        saveOutbox("PAYMENT", event.orderId(), "PAYMENT_FAILED", event);
    }

    @Override
    public void publishRefundCompleted(RefundCompletedEvent event) {
        saveOutbox("PAYMENT", event.orderId(), "REFUND_COMPLETED", event);
    }

    @Override
    public void publishDepositForfeited(DepositForfeitedEvent event) {
        saveOutbox("PAYMENT", event.orderId(), "DEPOSIT_FORFEITED", event);
    }

    @Override
    public void publishRefundRequest(UUID orderId, UUID paymentId, int cancelAmount, String cancelReason) {
        // REFUND_REQUEST는 Kafka 발행이 아닌 Toss cancel API 호출용
        // payload에 필요한 정보를 담아 Outbox 적재
        Map<String, Object> payload = Map.of(
                "paymentId", paymentId.toString(),
                "cancelAmount", cancelAmount,
                "cancelReason", cancelReason
        );
        saveOutbox("PAYMENT", orderId, "REFUND_REQUEST", payload);
    }

    private void saveOutbox(String aggregateType, UUID aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            PaymentOutbox outbox = PaymentOutbox.create(aggregateType, aggregateId, eventType, payload);
            paymentOutboxJpaRepository.save(outbox);
            log.info("[OUTBOX] 이벤트 적재 완료 - eventType: {}, aggregateId: {}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("[OUTBOX] 이벤트 직렬화 실패 - eventType: {}, aggregateId: {}", eventType, aggregateId, e);
            throw new RuntimeException("Outbox 이벤트 직렬화 실패", e);
        }
    }
}
