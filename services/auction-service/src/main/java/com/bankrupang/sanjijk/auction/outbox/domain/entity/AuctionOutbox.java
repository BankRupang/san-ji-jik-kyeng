package com.bankrupang.sanjijk.auction.outbox.domain.entity;

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

import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;
import com.bankrupang.sanjijk.common.entity.BaseEntity;

@Entity
@Table(name = "p_auction_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionOutbox extends BaseEntity {

    private static final String AUCTION_AGGREGATE_TYPE = "AUCTION";

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuctionEventType eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public static AuctionOutbox create(
            UUID aggregateId,
            AuctionEventType eventType,
            String payload
    ) {
        AuctionOutbox outbox = new AuctionOutbox();

        outbox.aggregateType = AUCTION_AGGREGATE_TYPE;
        outbox.aggregateId = aggregateId;
        outbox.eventType = eventType;
        outbox.payload = payload;
        outbox.published = false;
        outbox.publishedAt = null;

        return outbox;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }
}
