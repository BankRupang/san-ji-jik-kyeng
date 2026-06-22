package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID paymentId,
        UUID orderId,
        UUID userId, // winnerId
        UUID sellerId, // NORMAL 일 경우에만 필요
        UUID auctionId,
        String auctionTitle,
        String paymentType,   // "NORMAL" | "REPAY"
        int amount,
        int finalPrice,
        LocalDateTime occurredAt
) {
}
