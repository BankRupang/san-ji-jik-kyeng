package com.bankrupang.sanjijk.bid.application.service;

import com.bankrupang.sanjijk.bid.domain.event.BidOvertakenEvent;
import com.bankrupang.sanjijk.bid.domain.exception.BidErrorCode;
import com.bankrupang.sanjijk.bid.domain.exception.BidException;
import com.bankrupang.sanjijk.bid.infrastructure.kafka.BidEventProducer;
import com.bankrupang.sanjijk.bid.presentation.dto.BidBroadcastDto;
import com.bankrupang.sanjijk.bid.presentation.dto.BidRequestDto;
import com.bankrupang.sanjijk.bid.presentation.dto.HighestBidResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.script.DefaultRedisScript;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    // AuctionEndScheduler는 5초 간격 폴링이라, Redis TTL이 종료 판정 시각과 정확히 같으면
    // 스케줄러가 읽기 전에 해시가 먼저 만료돼 유찰로 오판될 수 있어 버퍼를 둔다.
    private static final long END_CHECK_TTL_BUFFER_SECONDS = 60;

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BidEventProducer bidEventProducer;

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
            if (userId.equals(info.get("sellerId"))) {
                throw new BidException(BidErrorCode.SELLER_BID);
            }
            if (info.isEmpty()) {
                throw new BidException(BidErrorCode.AUCTION_NOT_FOUND);
            }

            String status = (String) info.get("status");

            if (!"PROGRESS".equals(status)) {
                throw new BidException(BidErrorCode.AUCTION_NOT_IN_PROGRESS);
            }

            LocalDateTime endAt = LocalDateTime.parse((String) info.get("endAt"));

            if (LocalDateTime.now().isAfter(endAt)) {
                throw new BidException(BidErrorCode.AUCTION_ENDED);
            }

            Integer currentPrice = Integer.parseInt((String) info.get("currentPrice"));

            if (!request.getClientSeenPrice().equals(currentPrice)) {
                throw new BidException(BidErrorCode.BID_PRICE_OUTDATED);
            }

            if (request.getBidPrice() <= currentPrice) {
                throw new BidException(BidErrorCode.BID_PRICE_TOO_LOW);
            }

            String highestBidderId = (String) info.get("highestBidderId");

            if (userId.toString().equals(highestBidderId)) {
                throw new BidException(BidErrorCode.ALREADY_HIGHEST_BIDDER);
            }


            Boolean hasPaid = redisTemplate.hasKey("auction:" + auctionId + ":deposit:" + userId);
            if (!Boolean.TRUE.equals(hasPaid)) {
                throw new BidException(BidErrorCode.DEPOSIT_NOT_PAID);
            }


            LocalDateTime newEndAt = LocalDateTime.now().plusMinutes(1);
            long newTtl = Duration.ofMinutes(1).getSeconds() + END_CHECK_TTL_BUFFER_SECONDS;
            redisTemplate.opsForHash().put(hashKey, "endAt", newEndAt.toString());
            DefaultRedisScript<Long> expireScript = new DefaultRedisScript<>(
                    "return redis.call('EXPIRE', KEYS[1], ARGV[1])", Long.class);
            redisTemplate.execute(expireScript, Collections.singletonList(hashKey), String.valueOf(newTtl));
            redisTemplate.opsForZSet().add("auction:endings", auctionId.toString(), newEndAt.atZone(ZoneId.of("Asia/Seoul")).toEpochSecond());
            log.info("입찰 처리 - auctionId: {}, newEndAt: {}", auctionId, newEndAt);

            redisTemplate.opsForHash().put(hashKey, "currentPrice", String.valueOf(request.getBidPrice()));
            redisTemplate.opsForHash().put(hashKey, "highestBidderId", userId.toString());
            log.info("현재가 갱신 - auctionId: {}, newPrice: {}, highestBidder: {}", auctionId, request.getBidPrice(), userId);

            Integer bidUnit = Integer.parseInt((String) info.get("bidUnit"));
            BidBroadcastDto broadcast = new BidBroadcastDto(
                    "BID_UPDATED",
                    auctionId.toString(),
                    request.getBidPrice(),
                    highestBidderId,
                    userId.toString(),
                    request.getBidPrice() + bidUnit
            );
            try {
                String payload = objectMapper.writeValueAsString(broadcast);
                redisTemplate.convertAndSend("auction:" + auctionId + ":bid-event", payload);
                log.info("브로드캐스트 발행 - channel: auction:{}:bid-event", auctionId);
            } catch (JsonProcessingException e) {
                log.error("브로드캐스트 직렬화 실패 - auctionId: {}", auctionId, e);
            }

            String productName = (String) info.get("productName");
            String previousBidderId = (highestBidderId == null || highestBidderId.isBlank())
                    ? "" : highestBidderId;
            BidOvertakenEvent overtakenEvent = new BidOvertakenEvent(
                    auctionId.toString(),
                    productName,
                    previousBidderId,
                    request.getBidPrice(),
                    request.getBidPrice() + bidUnit,
                    LocalDateTime.now()
            );
            bidEventProducer.sendBidOvertaken(overtakenEvent);

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

    public HighestBidResponse getHighestBid(UUID auctionId) {
        log.info("최고가 조회 요청 (폴백) - auctionId: {}", auctionId);

        Map<Object, Object> info = redisTemplate.opsForHash()
                .entries("auction:" + auctionId + ":info");

        if (info.isEmpty()) {
            throw new BidException(BidErrorCode.AUCTION_NOT_FOUND);
        }

        String highestBidderId = (String) info.get("highestBidderId");
        String currentPrice = (String) info.get("currentPrice");

        boolean hasBid = highestBidderId != null && !highestBidderId.isBlank();

        if (!hasBid) {
            log.info("입찰자 없음 - auctionId: {}", auctionId);
            return null;
        }

        log.info("최고가 조회 완료 - auctionId: {}, winnerId: {}, finalPrice: {}", auctionId, highestBidderId, currentPrice);
        return new HighestBidResponse(UUID.fromString(highestBidderId), Integer.parseInt(currentPrice));
    }
}
