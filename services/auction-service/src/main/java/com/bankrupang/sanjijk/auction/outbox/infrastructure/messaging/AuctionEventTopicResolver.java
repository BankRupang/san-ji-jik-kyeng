package com.bankrupang.sanjijk.auction.outbox.infrastructure.messaging;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;

@Component
public class AuctionEventTopicResolver {

    private static final String AUCTION_START_TOPIC = "auction-start";
    private static final String AUCTION_EVENTS_TOPIC = "auction-events";

    public String resolve(AuctionEventType eventType) {
        Objects.requireNonNull(eventType, "이벤트 타입은 null일 수 없습니다.");

        return switch (eventType) {
            case AUCTION_START -> AUCTION_START_TOPIC;
            case AUCTION_WON, AUCTION_FAILED -> AUCTION_EVENTS_TOPIC;
        };
    }
}
