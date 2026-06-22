package com.bankrupang.sanjijk.auction.outbox.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;

public interface AuctionOutboxRepository extends JpaRepository<AuctionOutbox, UUID> {

    Page<AuctionOutbox> findByPublishedFalseOrderByCreatedAtAsc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AuctionOutbox o
            SET o.published = true,
                o.publishedAt = :publishedAt
            WHERE o.id IN :outboxIds
              AND o.published = false
            """)
    int markAllAsPublished(
            @Param("outboxIds") List<UUID> outboxIds,
            @Param("publishedAt") LocalDateTime publishedAt
    );
}
