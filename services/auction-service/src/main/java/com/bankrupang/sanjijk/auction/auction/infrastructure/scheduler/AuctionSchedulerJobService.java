package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.application.service.AuctionService;
import com.bankrupang.sanjijk.auction.auction.application.service.AuctionFallbackService;
import com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto.HighestBidResponse;
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
    private final ObjectProvider<AuctionSchedulerJobService> schedulerJobServiceProvider;
    private final AuctionFallbackService auctionFallbackService;
    private final ObjectProvider<AuctionService> auctionServiceProvider;

    @Transactional
    @SchedulerLock(name = "'auction-start-' + #auctionId", lockAtMostFor = "10m", lockAtLeastFor = "1s")
    public void startAuction(UUID auctionId) {
        AuctionLogContext.runWithAuctionId(auctionId, () -> {
            log.info("경매 스케줄러 잡 실행 - jobType: AUCTION_START, auctionId: {}", auctionId);
            auctionRepository.findByIdAndDeletedAtIsNull(auctionId)
                    .ifPresentOrElse(this::startAuctionIfReady, () ->
                            log.warn("스케줄러 시작 대상 경매를 찾을 수 없습니다. auctionId: {}", auctionId));
        });
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
                () -> schedulerJobServiceProvider.getObject().checkAuctionEnd(auction.getId())
        );
        log.info("스케줄러 경매 시작 완료 - auctionId: {}", auction.getId());
    }

    @SchedulerLock(name = "'auction-end-check-' + #auctionId", lockAtMostFor = "10m", lockAtLeastFor = "1s")
    public void checkAuctionEnd(UUID auctionId) {
        AuctionLogContext.runWithAuctionId(auctionId, () -> {
            log.info("경매 스케줄러 잡 실행 - jobType: AUCTION_END_CHECK, auctionId: {}", auctionId);
            auctionRepository.findByIdAndDeletedAtIsNull(auctionId)
                    .ifPresentOrElse(this::checkAuctionEndIfProgress, () ->
                            log.warn("스케줄러 마감 확인 대상 경매를 찾을 수 없습니다. auctionId: {}", auctionId));
        });
    }

    private void checkAuctionEndIfProgress(Auction auction) {
        if (auction.getStatus() != AuctionStatus.PROGRESS) {
            log.info("스케줄러 마감 확인 생략 - PROGRESS 상태가 아닙니다. auctionId: {}, status: {}",
                    auction.getId(), auction.getStatus());
            return;
        }

        log.info("스케줄러 마감 확인 시작 - bid-service 최고가 조회 폴백 시도. auctionId: {}", auction.getId());
        try {
            HighestBidResponse response = auctionFallbackService.getHighestBidWithRetry(auction.getId());

            boolean hasBid = response != null && response.winnerId() != null && response.finalPrice() != null;
            UUID winnerId = hasBid ? response.winnerId() : null;
            Long finalPrice = hasBid ? response.finalPrice().longValue() : null;

            auctionServiceProvider.getObject().closeAuctionByEndedEvent(
                    auction.getId(), hasBid, winnerId, finalPrice
            );
            log.info("스케줄러 마감 폴백 처리 완료 - auctionId: {}, hasBid: {}", auction.getId(), hasBid);
        } catch (Exception e) {
            log.error("경매 마감 폴백 최종 실패 - 운영자 확인 필요. auctionId: {}, status: {}",
                    auction.getId(), auction.getStatus(), e);
        }
    }
}
