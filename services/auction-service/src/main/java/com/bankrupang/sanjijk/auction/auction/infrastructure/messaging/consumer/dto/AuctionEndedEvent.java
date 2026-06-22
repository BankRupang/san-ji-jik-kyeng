package com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionEndedEvent(
        UUID auctionId,
        boolean hasBid,
        UUID winnerId,
        Long finalPrice,
        LocalDateTime endedAt
) {
}
