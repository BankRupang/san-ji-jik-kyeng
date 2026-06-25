package com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionFailedEvent(
        UUID auctionId,
        String auctionTitle,
        UUID sellerId,
        LocalDateTime occurredAt
) {
}
