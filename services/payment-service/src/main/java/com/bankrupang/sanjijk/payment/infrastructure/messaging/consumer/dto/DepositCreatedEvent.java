package com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepositCreatedEvent(
        UUID orderId,
        String orderNumber,
        UUID userId,
        UUID auctionId,
        String auctionTitle,
        int depositAmount,
        LocalDateTime endAt,       // 경매 종료 시각 - Redis TTL 계산용 (endAt + 2시간)
        LocalDateTime occurredAt
) {}
