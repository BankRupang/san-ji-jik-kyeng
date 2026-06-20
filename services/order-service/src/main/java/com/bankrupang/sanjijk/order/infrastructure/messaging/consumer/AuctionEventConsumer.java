package com.bankrupang.sanjijk.order.infrastructure.messaging.consumer;

import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.AuctionWonEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.handler.AuctionFailedHandler;
import com.bankrupang.sanjijk.order.infrastructure.messaging.handler.AuctionWonHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventConsumer {

    private final AuctionWonHandler auctionWonHandler;
    private final AuctionFailedHandler auctionFailedHandler;

    @KafkaListener(topics = "auction-won", groupId = "order-service")
    public void consumeAuctionWon(AuctionWonEvent event) {
        log.info("[KAFKA][CONSUME] AUCTION_WON 수신 - auctionId: {}, winnerId: {}, finalPrice: {}, occurredAt: {}",
                event.auctionId(), event.winnerId(), event.finalPrice(), event.occurredAt());
        auctionWonHandler.handle(event);
    }

    @KafkaListener(topics = "auction-failed", groupId = "order-service")
    public void consumeAuctionFailed(AuctionFailedEvent event) {
        log.info("[KAFKA][CONSUME] AUCTION_FAILED 수신 - auctionId: {}, sellerId: {}, occurredAt: {}",
                event.auctionId(), event.sellerId(), event.occurredAt());
        auctionFailedHandler.handle(event);
    }
}
