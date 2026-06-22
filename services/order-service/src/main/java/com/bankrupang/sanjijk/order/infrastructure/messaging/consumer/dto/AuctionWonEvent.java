package com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionWonEvent(
        UUID auctionId,
        String auctionTitle,
        UUID winnerId,
        UUID sellerId,
        int finalPrice,
        int depositAmount,
        LocalDateTime occurredAt
) {
}
