package com.bankrupang.sanjijk.auction.outbox.domain.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;

public interface AuctionOutboxRepository extends JpaRepository<AuctionOutbox, UUID> {

    Page<AuctionOutbox> findByPublishedFalseOrderByCreatedAtAsc(Pageable pageable);
}
