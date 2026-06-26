package com.bankrupang.sanjijk.order.infrastructure.messaging.producer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WinningCreatedEvent(
        UUID orderId,
        String orderNumber,
        UUID userId,
        UUID auctionId,
        String auctionTitle,
        UUID sellerId,
        int finalPrice,
        int depositAmount,
        int remainingAmount,
        LocalDateTime paymentDueAt,
        LocalDateTime occurredAt
) {
}
