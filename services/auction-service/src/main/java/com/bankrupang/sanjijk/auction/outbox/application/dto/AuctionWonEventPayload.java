package com.bankrupang.sanjijk.auction.outbox.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public record AuctionWonEventPayload(
        UUID auctionId,
        String auctionTitle,
        UUID winnerId,
        UUID sellerId,
        int finalPrice,
        int depositAmount,
        LocalDateTime occurredAt
) {
    public static AuctionWonEventPayload of(Auction auction, Product product, LocalDateTime occurredAt) {
        return new AuctionWonEventPayload(
                auction.getId(),
                product.getName(),
                auction.getWinnerId(),
                auction.getSellerId(),
                auction.getFinalPrice(),
                auction.getStartPrice(),
                occurredAt
        );
    }
}
