package com.bankrupang.sanjijk.notification.infrastructure.messaging.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class RefundCompletedEvent {

    private UUID paymentId;
    private UUID orderId;
    private UUID auctionId;
    private String auctionTitle;
    private UUID userId;
    private int cancelAmount;
    private String cancelReason;
    private LocalDateTime occurredAt;
}
