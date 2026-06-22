package com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepositForfeitedEvent(
        UUID paymentId,
        UUID orderId,
        UUID userId,
        UUID auctionId,
        String auctionTitle,
        int amount,
        LocalDateTime occurredAt
) {
}
