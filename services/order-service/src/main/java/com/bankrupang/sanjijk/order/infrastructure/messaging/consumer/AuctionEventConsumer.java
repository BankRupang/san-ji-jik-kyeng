package com.bankrupang.sanjijk.order.infrastructure.messaging.consumer;

import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.AuctionWonEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.handler.AuctionFailedHandler;
import com.bankrupang.sanjijk.order.infrastructure.messaging.handler.AuctionWonHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventConsumer {

    private final AuctionWonHandler auctionWonHandler;
    private final AuctionFailedHandler auctionFailedHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auction-events", groupId = "order-service")
    public void consumeAuctionEvent(ConsumerRecord<String, Map<String, Object>> record) {
        Map<String, Object> data = record.value();
        String eventType = (String) data.get("eventType");

        switch (eventType) {
            case "AUCTION_WON" -> {
                AuctionWonEvent event = objectMapper.convertValue(data, AuctionWonEvent.class);
                log.info("[KAFKA][CONSUME] AUCTION_WON 수신 - auctionId: {}, winnerId: {}, finalPrice: {}, occurredAt: {}",
                        event.auctionId(), event.winnerId(), event.finalPrice(), event.occurredAt());
                auctionWonHandler.handle(event);
            }
            case "AUCTION_FAILED" -> {
                AuctionFailedEvent event = objectMapper.convertValue(data, AuctionFailedEvent.class);
                log.info("[KAFKA][CONSUME] AUCTION_FAILED 수신 - auctionId: {}, occurredAt: {}",
                        event.auctionId(), event.occurredAt());
                auctionFailedHandler.handle(event);
            }
            default -> log.warn("[KAFKA][CONSUME] 알 수 없는 eventType 수신 - eventType: {}", eventType);
        }
    }
}
