package com.bankrupang.sanjijk.order.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.infrastructure.outbox.OrderOutboxJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisherTransaction {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    //payment-service가 order-events 토픽을 수신할 때 DEPOSIT_CREATED인지 WINNING_CREATED인지 구분
    private static final String DEPOSIT_CREATED_TOPIC = "deposit-created";
    private static final String WINNING_CREATED_TOPIC = "winning-created";

    @Transactional
    public void relayOne(OrderOutbox outbox) {
        try {
            Object payload = objectMapper.readValue(outbox.getPayload(), Object.class);
            String topic = resolveTopic(outbox.getEventType());
            kafkaTemplate.send(topic, outbox.getAggregateId().toString(), payload);
            outbox.markPublished();
            log.info("[OUTBOX] 이벤트 발행 완료 - outboxId: {}, eventType: {}, aggregateId: {}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId());
        } catch (Exception e) {
            outbox.markFailed();
            log.error("[OUTBOX] 이벤트 발행 실패 - outboxId: {}, eventType: {}, aggregateId: {}, retryCount: {}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId(), outbox.getRetryCount());
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "DEPOSIT_CREATED" -> DEPOSIT_CREATED_TOPIC;
            case "WINNING_CREATED" -> WINNING_CREATED_TOPIC;
            default -> throw new IllegalArgumentException("알 수 없는 이벤트 타입: " + eventType);
        };
    }
}