package com.bankrupang.sanjijk.bid.application.service;

import com.bankrupang.sanjijk.bid.domain.exception.BidErrorCode;
import com.bankrupang.sanjijk.bid.presentation.dto.BidRequestDto;
import com.bankrupang.sanjijk.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;

    public void bid(UUID auctionId, UUID userId, BidRequestDto request) {
        String lockKey = "auction:" + auctionId + ":lock";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RuntimeException("입찰 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }

            log.info("분산 락 획득 - auctionId: {}, userId: {}", auctionId, userId);

            String hashKey = "auction:" + auctionId + ":info";
            Map<Object, Object> info = redisTemplate.opsForHash().entries(hashKey);

            if (info.isEmpty()) {
                throw new BaseException(BidErrorCode.AUCTION_NOT_FOUND);
            }

            String status = (String) info.get("status");

            if ("READY".equals(status)) {
                LocalDateTime startAt = LocalDateTime.parse((String) info.get("startAt"));
                if (LocalDateTime.now().isAfter(startAt)) {
                    redisTemplate.opsForHash().put(hashKey, "status", "PROGRESS");
                    status = "PROGRESS";
                    log.info("경매 상태 PROGRESS 전환 - auctionId: {}", auctionId);
                }
            }

            if (!"PROGRESS".equals(status)) {
                throw new BaseException(BidErrorCode.AUCTION_NOT_IN_PROGRESS);
            }

            LocalDateTime endAt = LocalDateTime.parse((String) info.get("endAt"));

            if (LocalDateTime.now().isAfter(endAt)) {
                throw new BaseException(BidErrorCode.AUCTION_ENDED);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("분산 락 해제 - auctionId: {}", auctionId);
            }
        }
    }
}
