package com.bankrupang.sanjijk.auction.auction.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;

public record AuctionCreateResponse(
        UUID auctionId,
        UUID productId,
        UUID sellerId,
        AuctionStatus status,
        int startPrice,
        int bidUnit,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt
) {
    public static AuctionCreateResponse from(Auction auction) {
        return new AuctionCreateResponse(
                auction.getId(),
                auction.getProductId(),
                auction.getSellerId(),
                auction.getStatus(),
                auction.getStartPrice(),
                auction.getBidUnit(),
                auction.getStartAt(),
                auction.getEndAt(),
                auction.getCreatedAt()
        );
    }

}
