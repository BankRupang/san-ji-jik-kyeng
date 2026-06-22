package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.bankrupang.sanjijk.auction.global.util.AuctionLogContext;

@Slf4j
@Component
public class AuctionScheduleManager {

    private final ThreadPoolTaskScheduler taskScheduler;
    // TODO: 다중 인스턴스 환경에서는 동일 경매 잡이 중복 실행될 수 있으므로 ShedLock 적용 후 분산 락으로 보강한다.
    private final Map<UUID, ScheduledFuture<?>> startJobs = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> endCheckJobs = new ConcurrentHashMap<>();

    public AuctionScheduleManager(
            @Qualifier("auctionTaskScheduler") ThreadPoolTaskScheduler taskScheduler
    ) {
        this.taskScheduler = taskScheduler;
    }

    public void scheduleStartJob(UUID auctionId, LocalDateTime startAt, Runnable job) {
        AuctionLogContext.runWithAuctionId(auctionId, () -> {
            scheduleJob(startJobs, auctionId, startAt, job);
            log.info("경매 시작 잡 등록 - auctionId: {}, scheduledAt: {}", auctionId, startAt);
        });
    }

    public void scheduleEndCheckJob(UUID auctionId, LocalDateTime endAt, Runnable job) {
        AuctionLogContext.runWithAuctionId(auctionId, () -> {
            scheduleJob(endCheckJobs, auctionId, endAt, job);
            log.info("경매 마감 확인 잡 등록 - auctionId: {}, scheduledAt: {}", auctionId, endAt);
        });
    }

    public boolean cancelStartJob(UUID auctionId) {
        return AuctionLogContext.callWithAuctionId(auctionId, () -> {
            boolean cancelled = cancelJob(startJobs, auctionId);
            if (cancelled) {
                log.info("경매 시작 잡 취소 - auctionId: {}", auctionId);
            }
            return cancelled;
        });
    }

    public boolean cancelEndCheckJob(UUID auctionId) {
        return AuctionLogContext.callWithAuctionId(auctionId, () -> {
            boolean cancelled = cancelJob(endCheckJobs, auctionId);
            if (cancelled) {
                log.info("경매 마감 확인 잡 취소 - auctionId: {}", auctionId);
            }
            return cancelled;
        });
    }

    public boolean hasStartJob(UUID auctionId) {
        return startJobs.containsKey(auctionId);
    }

    public boolean hasEndCheckJob(UUID auctionId) {
        return endCheckJobs.containsKey(auctionId);
    }

    private void scheduleJob(
            Map<UUID, ScheduledFuture<?>> jobs,
            UUID auctionId,
            LocalDateTime scheduledAt,
            Runnable job
    ) {
        cancelJob(jobs, auctionId);

        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        Runnable wrappedJob = () -> {
            try {
                job.run();
            } finally {
                ScheduledFuture<?> future = futureRef.get();
                if (future != null) {
                    jobs.remove(auctionId, future);
                }
            }
        };

        ScheduledFuture<?> future = taskScheduler.schedule(
                wrappedJob,
                scheduledAt.atZone(ZoneId.systemDefault()).toInstant()
        );

        futureRef.set(future);
        jobs.put(auctionId, future);
    }

    private boolean cancelJob(Map<UUID, ScheduledFuture<?>> jobs, UUID auctionId) {
        ScheduledFuture<?> future = jobs.remove(auctionId);
        return future != null && future.cancel(false);
    }
}
