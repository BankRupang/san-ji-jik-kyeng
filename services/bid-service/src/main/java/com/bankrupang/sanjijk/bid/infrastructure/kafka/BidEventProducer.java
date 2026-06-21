package com.bankrupang.sanjijk.bid.infrastructure.kafka;

import com.bankrupang.sanjijk.bid.domain.event.AuctionEndedEvent;
import com.bankrupang.sanjijk.bid.domain.event.BidOvertakenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventProducer {

    private static final String BID_OVERTAKEN_TOPIC = "BID_OVERTAKEN";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendBidOvertaken(BidOvertakenEvent event) {
        kafkaTemplate.send(BID_OVERTAKEN_TOPIC, event.getAuctionId(), event);
        log.info("BID_OVERTAKEN 발행 - auctionId: {}, previousBidderId: {}", event.getAuctionId(), event.getPreviousBidderId());
    }

    public void sendAuctionEnded(AuctionEndedEvent event) {
        kafkaTemplate.send("AUCTION_ENDED", event.getAuctionId(), event);
        log.info("AUCTION_ENDED 발행 - auctionId: {}, winnerId: {}", event.getAuctionId(), event.getWinnerId());
    }
}
