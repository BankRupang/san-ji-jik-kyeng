package com.bankrupang.sanjijk.order.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.infrastructure.outbox.OrderOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher {

    private final OrderOutboxJpaRepository orderOutboxJpaRepository;

    /**
     * @Lazy self 주입 제거 → KafkaOrderEventPublisherTransaction 분리 -> OrderScheduler.java도 동일.
     * @Lazy + @RequiredArgsConstructor 조합에서 Lombok 생성자에 @Lazy가
     * 전파되지 않아 Spring Boot 2.6+에서 순환 참조로 기동 실패 가능성 있음.
     * @Transactional relayOne 메서드를 KafkaOrderEventPublisherTransaction으로
     * 분리해 해결.
     */
    private final KafkaOrderEventPublisherTransaction kafkaOrderEventPublisherTransaction;

    //payment-service가 order-events 토픽을 수신할 때 DEPOSIT_CREATED인지 WINNING_CREATED인지 구분
    private static final String DEPOSIT_CREATED_TOPIC = "deposit-created";
    private static final String WINNING_CREATED_TOPIC = "winning-created";

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<OrderOutbox> outboxList = orderOutboxJpaRepository.findRetryableOutboxes();
        for (OrderOutbox outbox : outboxList) {
            kafkaOrderEventPublisherTransaction.relayOne(outbox);
        }
    }
}
