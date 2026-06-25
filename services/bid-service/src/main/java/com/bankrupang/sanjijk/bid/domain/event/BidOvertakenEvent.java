package com.bankrupang.sanjijk.bid.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BidOvertakenEvent {
    private String auctionId;
    private String auctionTitle;
    private String previousBidderId;
    private Integer newPrice;
    private Integer nextMinPrice;
    private LocalDateTime occurredAt;
}
