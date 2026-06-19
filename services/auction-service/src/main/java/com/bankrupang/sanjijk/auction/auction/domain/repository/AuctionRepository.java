package com.bankrupang.sanjijk.auction.auction.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    Optional<Auction> findByIdAndDeletedAtIsNull(UUID auctionId);

}
