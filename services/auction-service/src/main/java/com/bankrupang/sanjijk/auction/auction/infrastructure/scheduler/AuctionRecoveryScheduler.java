package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.global.util.AuctionLogContext;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRecoveryScheduler {

    private final AuctionRepository auctionRepository;
    private final AuctionSchedulerJobService auctionSchedulerJobService;

    /**
     * 마감 시간이 초과되었음에도 여전히 PROGRESS 상태로 고착된 경매들을 주기적으로 감지하여 마감 처리합니다.
     * 기본 설정값은 5분마다 작동합니다. (fixedDelay = 300000)
     */
    @Scheduled(fixedDelayString = "${auction.recovery.scheduler.fixed-delay-ms:300000}")
    @SchedulerLock(name = "auction-recovery-scheduler", lockAtMostFor = "10m", lockAtLeastFor = "10s")
    public void recoverStuckAuctions() {
        log.info("[RECOVERY_SCHEDULER] 고착 경매 감시 스케줄러 시작");
        LocalDateTime now = LocalDateTime.now();
        List<Auction> stuckAuctions = auctionRepository.findAllByStatusAndEndAtBeforeAndDeletedAtIsNull(
                AuctionStatus.PROGRESS,
                now
        );

        if (stuckAuctions.isEmpty()) {
            log.info("[RECOVERY_SCHEDULER] 고착된 경매가 없습니다.");
            return;
        }

        log.warn("[RECOVERY_SCHEDULER] 고착된 경매 감지 - 건수: {}", stuckAuctions.size());

        for (Auction auction : stuckAuctions) {
            AuctionLogContext.runWithAuctionId(auction.getId(), () -> {
                try {
                    log.info("[RECOVERY_SCHEDULER] 고착 경매 복구(마감 확인) 처리 시도 - auctionId: {}, endAt: {}",
                            auction.getId(), auction.getEndAt());
                    auctionSchedulerJobService.checkAuctionEnd(auction.getId());
                } catch (Exception e) {
                    log.error("[RECOVERY_SCHEDULER] 고착 경매 복구 실패 - auctionId: {}", auction.getId(), e);
                }
            });
        }
        log.info("[RECOVERY_SCHEDULER] 고착 경매 감시 스케줄러 완료");
    }
}
