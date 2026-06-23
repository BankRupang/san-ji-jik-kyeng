package com.bankrupang.sanjijk.notification.infrastructure.messaging.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class RefundCompletedEvent {

    private UUID auctionId;
    private String auctionTitle;
    private UUID userId;
    private int cancelAmount;
    private LocalDateTime occurredAt;
}
