package com.bankrupang.sanjijk.auction.outbox.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public record AuctionStartEventPayload(
        UUID auctionId,
        String productName,
        LocalDateTime startAt,
        int startPrice,
        int bidUnit,
        AuctionStatus status
) {
    public static AuctionStartEventPayload of(Auction auction, Product product) {
        return new AuctionStartEventPayload(
                auction.getId(),
                product.getName(),
                auction.getStartAt(),
                auction.getStartPrice(),
                auction.getBidUnit(),
                auction.getStatus()
        );
    }
}
