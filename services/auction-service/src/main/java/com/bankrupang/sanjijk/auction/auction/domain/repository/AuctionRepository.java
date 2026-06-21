package com.bankrupang.sanjijk.auction.auction.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    Optional<Auction> findByIdAndDeletedAtIsNull(UUID auctionId);

    Optional<Auction> findByProductIdAndDeletedAtIsNull(UUID productId);

    Page<Auction> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Auction> findAllByStatusAndDeletedAtIsNull(AuctionStatus status, Pageable pageable);
}
