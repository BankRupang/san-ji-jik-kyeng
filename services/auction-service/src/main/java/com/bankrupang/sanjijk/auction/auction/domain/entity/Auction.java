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
import com.bankrupang.sanjijk.auction.auction.exception.AuctionErrorCode;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionException;
import com.bankrupang.sanjijk.common.entity.BaseEntity;

@Entity
@Table(name = "p_auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Column(name = "start_price", nullable = false)
    private int startPrice;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "final_price")
    private Integer finalPrice;

    @Column(name = "bid_unit", nullable = false)
    private int bidUnit;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    public static Auction create(
            UUID productId,
            UUID sellerId,
            int startPrice,
            int bidUnit,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        validateCreateRequest(productId, sellerId, startPrice, bidUnit, startAt, endAt);

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

    public void update(Integer startPrice, Integer bidUnit, LocalDateTime startAt) {
        if (startPrice != null) {
            this.startPrice = startPrice;
        }
        if (bidUnit != null) {
            this.bidUnit = bidUnit;
        }
        if (startAt != null) {
            this.startAt = startAt;
            this.endAt = startAt.plusHours(1);
        }
    }

    private static void validateCreateRequest(
            UUID productId,
            UUID sellerId,
            int startPrice,
            int bidUnit,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        if (productId == null || sellerId == null || startAt == null || endAt == null) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_REQUEST);
        }

        if (startPrice <= 0 || bidUnit <= 0) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_REQUEST);
        }

        if (!startAt.isBefore(endAt)) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_PERIOD);
        }
    }

    public void start() {
        if (status == AuctionStatus.PROGRESS) {
            return;
        }

        validateStatus(AuctionStatus.READY);
        this.status = AuctionStatus.PROGRESS;
    }

    public void markResultPending() {
        if (status == AuctionStatus.RESULT_PENDING) {
            return;
        }

        validateStatus(AuctionStatus.PROGRESS);
        this.status = AuctionStatus.RESULT_PENDING;
    }

    public void markWon(UUID winnerId, Integer finalPrice) {
        if (status == AuctionStatus.WON) {
            return;
        }

        validateStatus(AuctionStatus.RESULT_PENDING);
        validateWinningResult(winnerId, finalPrice);
        this.status = AuctionStatus.WON;
        this.winnerId = winnerId;
        this.finalPrice = finalPrice;
    }

    public void markSuccess() {
        if (status == AuctionStatus.SUCCESS) {
            return;
        }

        validateStatus(AuctionStatus.WON);
        this.status = AuctionStatus.SUCCESS;
    }

    public void markFailed() {
        if (status == AuctionStatus.FAIL) {
            return;
        }

        if (status != AuctionStatus.RESULT_PENDING && status != AuctionStatus.WON) {
            throw new AuctionException(AuctionErrorCode.INVALID_STATE_TRANSITION);
        }

        this.status = AuctionStatus.FAIL;
    }

    public void cancel(String cancelReason) {
        if (status == AuctionStatus.CANCELLED) {
            return;
        }

        validateStatus(AuctionStatus.READY);

        this.status = AuctionStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }

    public void validateEditable() {
        if (status != AuctionStatus.READY) {
            throw new AuctionException(AuctionErrorCode.AUCTION_NOT_EDITABLE);
        }
    }

    private void validateStatus(AuctionStatus requiredStatus) {
        if (status != requiredStatus) {
            throw new AuctionException(AuctionErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private void validateWinningResult(UUID winnerId, Integer finalPrice) {
        if (winnerId == null || finalPrice == null || finalPrice < startPrice) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_RESULT);
        }
    }

}
