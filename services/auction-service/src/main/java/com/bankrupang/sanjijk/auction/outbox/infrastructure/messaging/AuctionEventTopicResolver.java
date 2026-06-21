package com.bankrupang.sanjijk.auction.outbox.infrastructure.messaging;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;

@Component
public class AuctionEventTopicResolver {

    private static final String AUCTION_START_TOPIC = "auction-start";
    private static final String AUCTION_EVENTS_TOPIC = "auction-events";

    public String resolve(AuctionEventType eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");

        return switch (eventType) {
            case AUCTION_START -> AUCTION_START_TOPIC;
            case AUCTION_WON, AUCTION_FAILED -> AUCTION_EVENTS_TOPIC;
        };
    }
}
