package com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionExtendedEvent(
        UUID auctionId,
        LocalDateTime newEndAt
) {
}
