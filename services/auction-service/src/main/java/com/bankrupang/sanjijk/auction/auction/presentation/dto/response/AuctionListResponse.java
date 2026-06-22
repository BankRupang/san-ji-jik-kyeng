package com.bankrupang.sanjijk.auction.auction.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public record AuctionListResponse(
        UUID auctionId,
        String productName,
        AuctionStatus status,
        int bidUnit,
        int startPrice,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static AuctionListResponse of(Auction auction, Product product) {
        return new AuctionListResponse(
                auction.getId(),
                product.getName(),
                auction.getStatus(),
                auction.getBidUnit(),
                auction.getStartPrice(),
                auction.getStartAt(),
                auction.getEndAt()
        );
    }

}
