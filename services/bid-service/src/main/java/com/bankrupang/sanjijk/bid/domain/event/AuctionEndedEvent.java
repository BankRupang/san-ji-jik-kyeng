package com.bankrupang.sanjijk.bid.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AuctionEndedEvent {
    private String auctionId;
    private boolean hasBid;
    private String winnerId;
    private Long finalPrice;
    private LocalDateTime endedAt;
}
