package com.bankrupang.sanjijk.auction.auction.domain.repository;

import java.util.Collection;
import java.util.List;
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

    boolean existsByProductIdAndStatusInAndDeletedAtIsNull(UUID productId, Collection<AuctionStatus> statuses);

    Page<Auction> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Auction> findAllByStatusAndDeletedAtIsNull(AuctionStatus status, Pageable pageable);

    List<Auction> findAllByStatusInAndDeletedAtIsNull(Collection<AuctionStatus> statuses);

    List<Auction> findAllByStatusAndEndAtBeforeAndDeletedAtIsNull(AuctionStatus status, java.time.LocalDateTime dateTime);
}
