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

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentOutboxJpaRepository paymentOutboxJpaRepository;
    private final ObjectMapper objectMapper;

    // payment-service가 발행하는 토픽 목록
    // auction-service, order-service, notification-service가 수신
    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment-failed";
    private static final String REFUND_COMPLETED_TOPIC = "refund-completed";

    @Transactional
    public void relayOne(PaymentOutbox outbox) {
        try {
            Object payload = objectMapper.readValue(outbox.getPayload(), Object.class);
            String topic = resolveTopic(outbox.getEventType());

            // kafkaTemplate.send()는 비동기 → .get()으로 동기 대기
            // 실제 전송 실패 시 catch에 걸리게 함
            kafkaTemplate.send(topic, outbox.getAggregateId().toString(), payload)
                    .get(5, TimeUnit.SECONDS);

            outbox.markPublished();
            log.info("[OUTBOX] 이벤트 발행 완료 - outboxId: {}, eventType: {}, aggregateId: {}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId());

        } catch (Exception e) {
            outbox.markFailed(); // status = FAILED, retryCount++

            if (outbox.isRetryable()) {
                log.warn("[OUTBOX] 이벤트 발행 실패, 재시도 예정 - outboxId: {}, retryCount: {}",
                        outbox.getId(), outbox.getRetryCount());
            } else {
                // retryCount >= 3: 더 이상 findRetryableOutboxes에서 조회 안 됨
                log.error("[OUTBOX] 이벤트 발행 최대 재시도 초과 - outboxId: {}, eventType: {}, aggregateId: {}",
                        outbox.getId(), outbox.getEventType(), outbox.getAggregateId());
            }
        }

        // 성공(PUBLISHED) / 실패(FAILED) 모두 저장
        paymentOutboxJpaRepository.save(outbox);
    }

    // eventType → 토픽명 매핑
    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "PAYMENT_COMPLETED" -> PAYMENT_COMPLETED_TOPIC;
            case "PAYMENT_FAILED" -> PAYMENT_FAILED_TOPIC;
            case "REFUND_COMPLETED" -> REFUND_COMPLETED_TOPIC;
            default -> throw new IllegalArgumentException("알 수 없는 이벤트 타입: " + eventType);
        };
    }
}
