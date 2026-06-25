package com.bankrupang.sanjijk.bid.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BidBroadcastDto {
    private String type;
    private String auctionId;
    private Integer currentPrice;
    private String previousHighestBidderId;
    private String highestBidderId;
    private Integer nextMinPrice;
}
