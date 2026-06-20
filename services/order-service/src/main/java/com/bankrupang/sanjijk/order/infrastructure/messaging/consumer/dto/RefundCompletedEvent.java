package com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefundCompletedEvent(
        UUID orderId,
        UUID auctionId,
        String auctionTitle,
        UUID userId,
        UUID sellerId,
        int refundAmount,
        LocalDateTime occurredAt
) {
}
