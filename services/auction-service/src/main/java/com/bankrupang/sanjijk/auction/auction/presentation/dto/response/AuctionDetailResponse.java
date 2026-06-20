package com.bankrupang.sanjijk.auction.auction.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public record AuctionDetailResponse(
        UUID auctionId,
        ProductInfo product,
        UUID sellerId,
        AuctionStatus status,
        int bidUnit,
        int startPrice,
        UUID winnerId,
        Integer finalPrice,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static AuctionDetailResponse of(Auction auction, Product product) {
        return new AuctionDetailResponse(
                auction.getId(),
                ProductInfo.from(product),
                auction.getSellerId(),
                auction.getStatus(),
                auction.getBidUnit(),
                auction.getStartPrice(),
                auction.getWinnerId(),
                auction.getFinalPrice(),
                auction.getStartAt(),
                auction.getEndAt()
        );
    }

    public record ProductInfo(
            UUID productId,
            String name,
            String description,
            String quantity
    ) {
        public static ProductInfo from(Product product) {
            return new ProductInfo(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getQuantity()
            );
        }
    }
}
