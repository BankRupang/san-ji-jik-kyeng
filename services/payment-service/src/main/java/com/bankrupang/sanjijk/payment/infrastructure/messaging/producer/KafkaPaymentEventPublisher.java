package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.infrastructure.outbox.PaymentOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher {

    private final PaymentOutboxJpaRepository paymentOutboxJpaRepository;

    // @Lazy self 주입 대신 Transaction 클래스 분리
    // → @Transactional이 걸린 메서드를 같은 클래스에서 호출하면 프록시 우회로 트랜잭션 무시됨
    private final KafkaPaymentEventPublisherTransaction kafkaPaymentEventPublisherTransaction;

    // 5초마다 Outbox 테이블 폴링 → Kafka 발행
    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<PaymentOutbox> outboxList = paymentOutboxJpaRepository
                .findRetryableOutboxes(PageRequest.of(0, 100), PaymentOutbox.MAX_RETRY_COUNT);

        for (PaymentOutbox outbox : outboxList) {
            try {
                kafkaPaymentEventPublisherTransaction.relayOne(outbox);
            } catch (Exception e) {
                log.error("[OUTBOX] relay 처리 중 오류 - outboxId: {}", outbox.getId(), e);
            }
        }
    }
}
