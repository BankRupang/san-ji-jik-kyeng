package com.bankrupang.sanjijk.bid.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AuctionExtendedEvent {
    private String auctionId;
    private LocalDateTime newEndAt;
}
