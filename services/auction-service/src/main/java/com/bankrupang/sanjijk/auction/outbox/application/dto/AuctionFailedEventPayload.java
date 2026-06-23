package com.bankrupang.sanjijk.auction.outbox.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public record AuctionFailedEventPayload(
        AuctionEventType eventType,
        UUID auctionId,
        String auctionTitle,
        UUID sellerId,
        LocalDateTime occurredAt
) {
    public static AuctionFailedEventPayload of(Auction auction, Product product, LocalDateTime occurredAt) {
        return new AuctionFailedEventPayload(
                AuctionEventType.AUCTION_FAILED,
                auction.getId(),
                product.getName(),
                auction.getSellerId(),
                occurredAt
        );
    }
}
