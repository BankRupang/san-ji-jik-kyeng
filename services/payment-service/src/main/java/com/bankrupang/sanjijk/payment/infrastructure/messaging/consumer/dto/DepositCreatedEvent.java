package com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepositCreatedEvent(
        UUID orderId,
        UUID userId,
        UUID auctionId,
        String auctionTitle,
        int depositAmount,   // → Payment.amount
        LocalDateTime occurredAt
) {
}
