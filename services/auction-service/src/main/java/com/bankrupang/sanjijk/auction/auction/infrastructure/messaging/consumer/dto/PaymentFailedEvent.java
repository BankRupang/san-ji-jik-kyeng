package com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID orderId,
        UUID auctionId,
        String auctionTitle,
        UUID winnerId,
        UUID sellerId,
        Integer finalPrice,
        String failureMessage,
        LocalDateTime occurredAt
) {
}
