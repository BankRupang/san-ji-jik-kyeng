package com.bankrupang.sanjijk.auction.outbox.infrastructure.messaging;

import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;
import com.bankrupang.sanjijk.auction.outbox.domain.repository.AuctionOutboxRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionOutboxRelay {

    private final AuctionOutboxRepository auctionOutboxRepository;
    private final AuctionEventTopicResolver auctionEventTopicResolver;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${auction.outbox.relay.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${auction.outbox.relay.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        auctionOutboxRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, batchSize))
                .forEach(this::publishEvent);
    }

    private void publishEvent(AuctionOutbox outbox) {
        String topic = auctionEventTopicResolver.resolve(outbox.getEventType());
        String key = outbox.getAggregateId().toString();

        try {
            kafkaTemplate.send(topic, key, outbox.getPayload()).get();
            outbox.markPublished();
            log.info("경매 Outbox 이벤트 발행 완료 - outboxId: {}, eventType: {}, topic: {}",
                    outbox.getId(), outbox.getEventType(), topic);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("경매 Outbox 이벤트 발행 중 인터럽트 발생 - outboxId: {}, eventType: {}, topic: {}",
                    outbox.getId(), outbox.getEventType(), topic, e);
        } catch (ExecutionException e) {
            log.warn("경매 Outbox 이벤트 발행 실패 - outboxId: {}, eventType: {}, topic: {}",
                    outbox.getId(), outbox.getEventType(), topic, e);
        }
    }
}
