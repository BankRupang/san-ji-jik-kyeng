package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID paymentId,
        UUID orderId,
        UUID userId, // winnerId
        UUID auctionId,
        String auctionTitle,
        int amount,
        String paymentType,
        String failureCode,
        String failureMessage,
        LocalDateTime occurredAt
) {
}
