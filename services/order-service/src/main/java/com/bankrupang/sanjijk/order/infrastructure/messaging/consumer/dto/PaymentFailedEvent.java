package com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID orderId,
        UUID auctionId,
        String auctionTitle,
        UUID winnerId,
        UUID sellerId,
        int finalPrice,
        String failureMessage,
        LocalDateTime occurredAt
) {
}
