package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer;

import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.domian.enums.OutboxStatus;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.handler.RefundRequestHandler;
import com.bankrupang.sanjijk.payment.infrastructure.outbox.PaymentOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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

        if (outboxList.isEmpty()) return;

        List<UUID> ids = outboxList.stream().map(PaymentOutbox::getId).toList();

        // REQUIRES_NEW: 즉시 커밋하여 다른 인스턴스가 IN_PROGRESS를 볼 수 있도록 함
        int updated = kafkaPaymentEventPublisherTransaction.claimBatch(ids);
        if (updated == 0) {
            log.debug("[OUTBOX] 선점 실패 - 다른 인스턴스가 처리 중");
            return;
        }

        // 실제 선점된 항목만 처리 (FAILED 항목 일부 미선점 가능)
        List<PaymentOutbox> claimedOutboxes = paymentOutboxJpaRepository
                .findByIdInAndStatus(ids, OutboxStatus.IN_PROGRESS);

        for (PaymentOutbox outbox : claimedOutboxes) {
            try {
                if ("REFUND_REQUEST".equals(outbox.getEventType())) {
                    refundRequestHandler.handle(outbox);
                } else {
                    kafkaPaymentEventPublisherTransaction.relayOne(outbox);
                }
            } catch (Exception e) {
                log.error("[OUTBOX] relay 처리 중 오류 - outboxId: {}, eventType: {}",
                        outbox.getId(), outbox.getEventType(), e);
                // REQUIRES_NEW: IN_PROGRESS 고착 방지
                kafkaPaymentEventPublisherTransaction.markOneFailed(outbox.getId());
            }
        }
    }
}
