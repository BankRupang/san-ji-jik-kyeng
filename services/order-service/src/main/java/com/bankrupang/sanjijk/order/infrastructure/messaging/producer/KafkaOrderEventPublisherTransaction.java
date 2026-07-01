package com.bankrupang.sanjijk.order.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.infrastructure.outbox.OrderOutboxJpaRepository;
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
public class KafkaOrderEventPublisherTransaction {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OrderOutboxJpaRepository orderOutboxJpaRepository;

    private static final String DEPOSIT_CREATED_TOPIC = "deposit-created";
    private static final String WINNING_CREATED_TOPIC = "winning-created";
    private static final String AUCTION_FAILED_TOPIC = "auction-failed";
    private static final String DEPOSIT_FORFEITED_TOPIC = "deposit-forfeited";

    @Transactional
    public void relayOne(OrderOutbox outbox) {
        try {
            Object payload = objectMapper.readValue(outbox.getPayload(), Object.class);
            String topic = resolveTopic(outbox.getEventType());

            //kafkaTemplate.send()가 비동기라 전송 실패해도 catch에 안 걸리는 문제
            // -> 일단 동기 대기로 실제 전송 실패 시 catch에 걸리게
            kafkaTemplate.send(topic, outbox.getAggregateId().toString(), payload).get(5, TimeUnit.SECONDS);
            outbox.markPublished();
            log.info("[OUTBOX] 이벤트 발행 완료 - outboxId: {}, eventType: {}, aggregateId: {}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId());
        } catch (Exception e) {
            outbox.markFailed();
            log.error("[OUTBOX] 이벤트 발행 실패 - outboxId: {}, eventType: {}, aggregateId: {}, retryCount: {}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId(), outbox.getRetryCount());
        }
        orderOutboxJpaRepository.save(outbox); //DB에 저장 안 되는 부분 해결
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "DEPOSIT_CREATED" -> DEPOSIT_CREATED_TOPIC;
            case "WINNING_CREATED" -> WINNING_CREATED_TOPIC;
            case "REFUND_REQUESTED" -> AUCTION_FAILED_TOPIC;
            case "DEPOSIT_FORFEITED" -> DEPOSIT_FORFEITED_TOPIC;
            default -> throw new IllegalArgumentException("알 수 없는 이벤트 타입: " + eventType);
        };
    }
}
