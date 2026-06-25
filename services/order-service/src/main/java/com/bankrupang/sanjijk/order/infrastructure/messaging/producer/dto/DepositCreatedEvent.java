package com.bankrupang.sanjijk.order.infrastructure.messaging.producer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepositCreatedEvent(
        UUID orderId,
        String orderNumber,
        UUID userId,
        UUID auctionId,
        String auctionTitle,
        int depositAmount,
        LocalDateTime endAt,
        LocalDateTime occurredAt
) {
}
