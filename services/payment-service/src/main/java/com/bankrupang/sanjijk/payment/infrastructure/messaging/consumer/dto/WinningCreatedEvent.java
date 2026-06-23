package com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WinningCreatedEvent(
        UUID orderId,
        UUID userId,
        UUID auctionId,
        String auctionTitle,
        UUID sellerId,
        int finalPrice,
        int depositAmount,
        int remainingAmount,  // → Payment.amount (잔금)
        LocalDateTime paymentDueAt,
        LocalDateTime occurredAt
) {
}
