package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.infrastructure.outbox.PaymentOutboxJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisherTransaction {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentOutboxJpaRepository paymentOutboxJpaRepository;
    private final ObjectMapper objectMapper;

    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment-failed";
    private static final String REFUND_COMPLETED_TOPIC = "refund-completed";
    private static final String DEPOSIT_FORFEITED_TOPIC = "deposit-forfeited";

    @Transactional
    public void relayOne(PaymentOutbox outbox) {
        try {
            String topic = resolveTopic(outbox.getEventType());

            kafkaTemplate.send(topic, outbox.getAggregateId().toString(), outbox.getPayload())
                    .get(5, TimeUnit.SECONDS);

            outbox.markPublished();
            log.info("[OUTBOX] 이벤트 발행 완료 - outboxId: {}, eventType: {}, aggregateId: {}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId());

        } catch (Exception e) {
            outbox.markFailed();

            if (outbox.isRetryable()) {
                log.warn("[OUTBOX] 이벤트 발행 실패, 재시도 예정 - outboxId: {}, retryCount: {}",
                        outbox.getId(), outbox.getRetryCount());
            } else {
                log.error("[OUTBOX] 이벤트 발행 최대 재시도 초과 - outboxId: {}, eventType: {}, aggregateId: {}",
                        outbox.getId(), outbox.getEventType(), outbox.getAggregateId());
            }
        }

        paymentOutboxJpaRepository.save(outbox);
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "PAYMENT_COMPLETED" -> PAYMENT_COMPLETED_TOPIC;
            case "PAYMENT_FAILED" -> PAYMENT_FAILED_TOPIC;
            case "REFUND_COMPLETED" -> REFUND_COMPLETED_TOPIC;
            case "DEPOSIT_FORFEITED" -> DEPOSIT_FORFEITED_TOPIC;
            default -> throw new IllegalArgumentException("알 수 없는 이벤트 타입: " + eventType);
        };
    }
}
