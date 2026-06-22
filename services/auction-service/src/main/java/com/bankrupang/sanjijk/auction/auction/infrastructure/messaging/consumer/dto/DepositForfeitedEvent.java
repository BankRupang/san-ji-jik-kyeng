package com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepositForfeitedEvent(
        UUID orderId,
        UUID auctionId,
        String auctionTitle,
        UUID winnerId,
        UUID sellerId,
        Integer forfeitedAmount,
        LocalDateTime occurredAt
) {
}
