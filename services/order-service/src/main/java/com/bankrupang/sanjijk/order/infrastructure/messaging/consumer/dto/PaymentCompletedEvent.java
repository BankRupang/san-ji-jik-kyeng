package com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID orderId,
        UUID auctionId,
        String auctionTitle,
        UUID winnerId,
        UUID sellerId,
        int finalPrice,
        int paidAmount,
        String paymentType,
        LocalDateTime occurredAt
) {
}
