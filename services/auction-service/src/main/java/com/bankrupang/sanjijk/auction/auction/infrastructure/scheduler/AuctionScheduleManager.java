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

@Component
public class AuctionScheduleManager {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<UUID, ScheduledFuture<?>> startJobs = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> endCheckJobs = new ConcurrentHashMap<>();

    public AuctionScheduleManager(
            @Qualifier("auctionTaskScheduler") ThreadPoolTaskScheduler taskScheduler
    ) {
        this.taskScheduler = taskScheduler;
    }

    public void scheduleStartJob(UUID auctionId, LocalDateTime startAt, Runnable job) {
        scheduleJob(startJobs, auctionId, startAt, job);
    }

    public void scheduleEndCheckJob(UUID auctionId, LocalDateTime endAt, Runnable job) {
        scheduleJob(endCheckJobs, auctionId, endAt, job);
    }

    public boolean cancelStartJob(UUID auctionId) {
        return cancelJob(startJobs, auctionId);
    }

    public boolean cancelEndCheckJob(UUID auctionId) {
        return cancelJob(endCheckJobs, auctionId);
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
