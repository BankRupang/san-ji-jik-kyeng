package com.bankrupang.sanjijk.order.infrastructure.messaging.producer.dto;

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
