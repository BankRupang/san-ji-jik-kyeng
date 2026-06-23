package com.bankrupang.sanjijk.notification.infrastructure.messaging.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class BidOvertakenEvent {

    private String auctionId;
    private String auctionTitle;
    private String previousBidderId;
    private Long newPrice;
    private Long nextMinPrice;
    private LocalDateTime occurredAt;
}