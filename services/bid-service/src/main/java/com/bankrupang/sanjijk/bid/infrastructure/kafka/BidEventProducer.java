package com.bankrupang.sanjijk.bid.infrastructure.kafka;

import com.bankrupang.sanjijk.bid.domain.event.AuctionEndedEvent;
import com.bankrupang.sanjijk.bid.domain.event.AuctionExtendedEvent;
import com.bankrupang.sanjijk.bid.domain.event.BidOvertakenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventProducer {

    private static final String BID_OVERTAKEN_TOPIC = "bid-overtaken";
    private static final String AUCTION_EXTENDED_TOPIC = "auction-extended";
    private static final String AUCTION_ENDED_TOPIC = "auction-ended";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendBidOvertaken(BidOvertakenEvent event) {
        kafkaTemplate.send(BID_OVERTAKEN_TOPIC, event.getAuctionId(), event);
        log.info("bid-overtaken 발행 - auctionId: {}, previousBidderId: {}", event.getAuctionId(), event.getPreviousBidderId());
    }

    public void sendAuctionExtended(AuctionExtendedEvent event) {
        kafkaTemplate.send(AUCTION_EXTENDED_TOPIC, event.getAuctionId(), event);
        log.info("auction-extended 발행 - auctionId: {}, newEndAt: {}", event.getAuctionId(), event.getNewEndAt());
    }

    public void sendAuctionEnded(AuctionEndedEvent event) {
        kafkaTemplate.send(AUCTION_ENDED_TOPIC, event.getAuctionId(), event);
        log.info("auction-ended 발행 - auctionId: {}, winnerId: {}", event.getAuctionId(), event.getWinnerId());
    }
}
