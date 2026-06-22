package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.handler.RefundRequestHandler;
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
    private final KafkaPaymentEventPublisherTransaction kafkaPaymentEventPublisherTransaction;
    private final RefundRequestHandler refundRequestHandler;

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<PaymentOutbox> outboxList = paymentOutboxJpaRepository
                .findRetryableOutboxes(PageRequest.of(0, 100), PaymentOutbox.MAX_RETRY_COUNT);

        for (PaymentOutbox outbox : outboxList) {
            try {
                if ("REFUND_REQUEST".equals(outbox.getEventType())) {
                    refundRequestHandler.handle(outbox);
                } else {
                    kafkaPaymentEventPublisherTransaction.relayOne(outbox);
                }
            } catch (Exception e) {
                log.error("[OUTBOX] relay 처리 중 오류 - outboxId: {}", outbox.getId(), e);
            }
        }
    }
}
