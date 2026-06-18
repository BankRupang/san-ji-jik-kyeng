package com.bankrupang.sanjijk.notification.infrastructure.messaging.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class PaymentFailedEvent {

    private UUID auctionId;
    private String auctionTitle;
    private UUID winnerId;
    private UUID sellerId;
    private int finalPrice;
    private String failureMessage;
    private LocalDateTime occurredAt;
}
