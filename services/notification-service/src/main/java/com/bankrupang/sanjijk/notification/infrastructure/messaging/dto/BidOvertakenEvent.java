package com.bankrupang.sanjijk.notification.infrastructure.messaging.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class BidOvertakenEvent {

    private UUID auctionId;
    private String auctionTitle;
    private UUID previousBidderId;
    private int newPrice;
    private int nextMinPrice;
    private LocalDateTime occurredAt;
}