package com.bankrupang.sanjijk.bid.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class AuctionStartedEvent {

    private UUID auctionId;
    private String productName;
    private String status;
    private LocalDateTime startAt;
    private Long startPrice;
    private Long bidUnit;
}
