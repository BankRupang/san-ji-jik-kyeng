package com.bankrupang.sanjijk.auction.auction.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.common.entity.BaseEntity;

@Entity
@Table(name = "p_auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    UUID productId;

    @Column(name = "seller_id", nullable = false)
    UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    AuctionStatus status;

    @Column(name = "start_price", nullable = false)
    Integer startPrice;

    @Column(name = "winner_id")
    UUID winnerId;

    @Column(name = "final_price")
    Integer finalPrice;

    @Column(name = "bid_unit", nullable = false)
    Integer bidUnit;

    @Column(name = "extension_count", nullable = false)
    Integer extensionCount;

    @Column(name = "start_at", nullable = false)
    LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    LocalDateTime endAt;

    public static Auction create(
            UUID productId,
            UUID sellerId,
            Integer startPrice,
            Integer bidUnit,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        Auction auction = new Auction();

        auction.productId = productId;
        auction.sellerId = sellerId;
        auction.status = AuctionStatus.READY;
        auction.startPrice = startPrice;
        auction.winnerId = null;
        auction.finalPrice = null;
        auction.bidUnit = bidUnit;
        auction.extensionCount = 0;
        auction.startAt = startAt;
        auction.endAt = endAt;

        return auction;
    }

}
