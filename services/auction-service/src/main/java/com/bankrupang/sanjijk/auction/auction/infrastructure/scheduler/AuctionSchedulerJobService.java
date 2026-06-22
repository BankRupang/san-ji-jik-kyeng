package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.outbox.application.service.AuctionOutboxService;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionSchedulerJobService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final AuctionOutboxService auctionOutboxService;

    @Transactional
    public void startAuction(UUID auctionId) {
        auctionRepository.findByIdAndDeletedAtIsNull(auctionId)
                .ifPresentOrElse(this::startAuctionIfReady, () ->
                        log.warn("스케줄러 시작 대상 경매를 찾을 수 없습니다. auctionId: {}", auctionId));
    }

    private void startAuctionIfReady(Auction auction) {
        if (auction.getStatus() != AuctionStatus.READY) {
            log.info("스케줄러 시작 생략 - READY 상태가 아닙니다. auctionId: {}, status: {}",
                    auction.getId(), auction.getStatus());
            return;
        }

        Product product = productRepository.findByIdAndDeletedAtIsNull(auction.getProductId())
                .orElse(null);

        if (product == null) {
            log.warn("스케줄러 시작 대상 상품을 찾을 수 없습니다. auctionId: {}, productId: {}",
                    auction.getId(), auction.getProductId());
            return;
        }

        auction.start();
        auctionOutboxService.saveAuctionStartEvent(auction, product);
        log.info("스케줄러 경매 시작 완료 - auctionId: {}", auction.getId());
    }
}
