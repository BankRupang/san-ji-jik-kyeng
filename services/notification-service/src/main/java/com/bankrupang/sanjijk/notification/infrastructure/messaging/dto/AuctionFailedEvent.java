package com.bankrupang.sanjijk.notification.infrastructure.messaging.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class AuctionFailedEvent {

    private UUID auctionId;
    private String auctionTitle;
    private UUID sellerId;
    private LocalDateTime occurredAt;
}
