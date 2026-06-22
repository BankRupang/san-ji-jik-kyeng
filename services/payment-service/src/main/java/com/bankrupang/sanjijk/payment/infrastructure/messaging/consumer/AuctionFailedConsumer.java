package com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer;

import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.handler.AuctionFailedHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionFailedConsumer {

    private final AuctionFailedHandler auctionFailedHandler;

    @KafkaListener(topics = "auction-failed", groupId = "payment-service")
    public void consume(AuctionFailedEvent event) {
        log.info("[CONSUMER] AUCTION_FAILED 수신 - auctionId: {}",
                event.auctionId());
        auctionFailedHandler.handle(event);
    }
}
