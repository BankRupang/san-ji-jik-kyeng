package com.bankrupang.sanjijk.order.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.domain.enums.OutboxStatus;
import com.bankrupang.sanjijk.order.infrastructure.outbox.OrderOutboxJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher {

    private final OrderOutboxJpaRepository orderOutboxJpaRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relay() {
        List<OrderOutbox> outboxList = orderOutboxJpaRepository.findByStatus(OutboxStatus.PENDING);

        for (OrderOutbox outbox : outboxList) {
            try {
                Object payload = objectMapper.readValue(outbox.getPayload(), Object.class);
                kafkaTemplate.send(ORDER_EVENTS_TOPIC, outbox.getAggregateId().toString(), payload);
                outbox.markPublished();
            } catch (Exception e) {
                outbox.markFailed();
            }
        }
    }
}
