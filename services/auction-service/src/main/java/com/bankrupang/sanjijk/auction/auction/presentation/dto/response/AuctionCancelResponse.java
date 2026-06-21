package com.bankrupang.sanjijk.auction.auction.presentation.dto.response;

import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;

public record AuctionCancelResponse(
        UUID auctionId,
        AuctionStatus status,
        String reason
) {
    public static AuctionCancelResponse from(Auction auction) {
        return new AuctionCancelResponse(
                auction.getId(),
                auction.getStatus(),
                auction.getCancelReason()
        );
    }

}
