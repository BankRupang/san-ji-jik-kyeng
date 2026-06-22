package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID orderId,
        UUID auctionId,
        String auctionTitle,
        UUID winnerId,
        UUID sellerId,
        Integer finalPrice,
        Integer paidAmount,
        String paymentType,
        LocalDateTime occurredAt
) {
}
