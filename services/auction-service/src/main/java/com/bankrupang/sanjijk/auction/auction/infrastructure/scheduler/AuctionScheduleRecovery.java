package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduleRecovery {

    private final AuctionRepository auctionRepository;
    private final AuctionScheduleManager auctionScheduleManager;
    private final AuctionSchedulerJobService auctionSchedulerJobService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverSchedules() {
        List<Auction> auctions = auctionRepository.findAllByStatusInAndDeletedAtIsNull(List.of(
                AuctionStatus.READY,
                AuctionStatus.PROGRESS
        ));
        LocalDateTime now = LocalDateTime.now();

        auctions.forEach(auction -> recoverSchedule(auction, now));
        log.info("경매 스케줄 복구 완료 - 대상 경매 수: {}", auctions.size());
    }

    private void recoverSchedule(Auction auction, LocalDateTime now) {
        if (auction.getStatus() == AuctionStatus.READY) {
            recoverStartSchedule(auction, now);
            return;
        }

        if (auction.getStatus() == AuctionStatus.PROGRESS) {
            recoverEndCheckSchedule(auction, now);
        }
    }

    private void recoverStartSchedule(Auction auction, LocalDateTime now) {
        if (!auction.getStartAt().isAfter(now)) {
            log.warn("시작 시간이 지난 READY 경매는 자동 시작하지 않습니다. auctionId: {}, startAt: {}",
                    auction.getId(), auction.getStartAt());
            return;
        }

        auctionScheduleManager.scheduleStartJob(
                auction.getId(),
                auction.getStartAt(),
                () -> auctionSchedulerJobService.startAuction(auction.getId())
        );
    }

    private void recoverEndCheckSchedule(Auction auction, LocalDateTime now) {
        if (!auction.getEndAt().isAfter(now)) {
            auctionSchedulerJobService.checkAuctionEnd(auction.getId());
            return;
        }

        auctionScheduleManager.scheduleEndCheckJob(
                auction.getId(),
                auction.getEndAt(),
                () -> auctionSchedulerJobService.checkAuctionEnd(auction.getId())
        );
    }
}
