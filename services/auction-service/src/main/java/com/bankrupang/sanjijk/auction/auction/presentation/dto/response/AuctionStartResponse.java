package com.bankrupang.sanjijk.auction.auction.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;

public record AuctionStartResponse(
        UUID auctionId,
        AuctionStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime updatedAt
) {
    public static AuctionStartResponse from(Auction auction) {
        return new AuctionStartResponse(
                auction.getId(),
                auction.getStatus(),
                auction.getStartAt(),
                auction.getEndAt(),
                auction.getUpdatedAt()
        );
    }

}
