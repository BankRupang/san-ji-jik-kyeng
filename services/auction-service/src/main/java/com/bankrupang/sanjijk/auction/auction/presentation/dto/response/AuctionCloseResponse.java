package com.bankrupang.sanjijk.auction.auction.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;

public record AuctionCloseResponse(
        UUID auctionId,
        AuctionStatus status,
        UUID winnerId,
        Integer finalPrice,
        LocalDateTime updatedAt
) {
    public static AuctionCloseResponse from(Auction auction) {
        return new AuctionCloseResponse(
                auction.getId(),
                auction.getStatus(),
                auction.getWinnerId(),
                auction.getFinalPrice(),
                auction.getUpdatedAt()
        );
    }

}
