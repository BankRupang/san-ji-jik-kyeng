package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.global.util.AuctionLogContext;
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
    private final AuctionScheduleManager auctionScheduleManager;

    @Transactional
    public void startAuction(UUID auctionId) {
        AuctionLogContext.runWithAuctionId(auctionId, () ->
                auctionRepository.findByIdAndDeletedAtIsNull(auctionId)
                        .ifPresentOrElse(this::startAuctionIfReady, () ->
                                log.warn("스케줄러 시작 대상 경매를 찾을 수 없습니다. auctionId: {}", auctionId)));
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
        auctionScheduleManager.scheduleEndCheckJob(
                auction.getId(),
                auction.getEndAt(),
                () -> checkAuctionEnd(auction.getId())
        );
        log.info("스케줄러 경매 시작 완료 - auctionId: {}", auction.getId());
    }

    @Transactional(readOnly = true)
    public void checkAuctionEnd(UUID auctionId) {
        AuctionLogContext.runWithAuctionId(auctionId, () ->
                auctionRepository.findByIdAndDeletedAtIsNull(auctionId)
                        .ifPresentOrElse(this::checkAuctionEndIfProgress, () ->
                                log.warn("스케줄러 마감 확인 대상 경매를 찾을 수 없습니다. auctionId: {}", auctionId)));
    }

    private void checkAuctionEndIfProgress(Auction auction) {
        if (auction.getStatus() != AuctionStatus.PROGRESS) {
            log.info("스케줄러 마감 확인 생략 - PROGRESS 상태가 아닙니다. auctionId: {}, status: {}",
                    auction.getId(), auction.getStatus());
            return;
        }

        // TODO: AUCTION_ENDED 이벤트 수신 로직 구현 후 스케줄러 마감 확인 잡의 역할을 재검토한다.
        log.info("스케줄러 마감 확인 완료 - bid-service의 AUCTION_ENDED 이벤트를 대기합니다. auctionId: {}",
                auction.getId());
    }
}
