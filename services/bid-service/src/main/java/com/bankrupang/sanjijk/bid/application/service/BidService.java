package com.bankrupang.sanjijk.bid.application.service;

import com.bankrupang.sanjijk.bid.domain.event.AuctionExtendedEvent;
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
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

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

            String highestBidderId = (String) info.get("highestBidderId");

            if (userId.toString().equals(highestBidderId)) {
                throw new BidException(BidErrorCode.ALREADY_HIGHEST_BIDDER);
            }


            //Boolean hasPaid = redisTemplate.hasKey("auction:" + auctionId + ":deposit:" + userId);

            if (Duration.between(LocalDateTime.now(), endAt).getSeconds() <= 30) {
                LocalDateTime newEndAt = endAt.plusMinutes(1);
                long newTtl = Duration.between(LocalDateTime.now(), newEndAt).getSeconds();
                redisTemplate.opsForHash().put(hashKey, "endAt", newEndAt.toString());
                redisTemplate.expire(hashKey, Duration.ofSeconds(newTtl));
                redisTemplate.opsForZSet().add("auction:endings", auctionId.toString(), newEndAt.toEpochSecond(ZoneOffset.UTC));
                log.info("안티스나이핑 발동 - auctionId: {}, newEndAt: {}", auctionId, newEndAt);
                bidEventProducer.sendAuctionExtended(new AuctionExtendedEvent(auctionId.toString(), newEndAt));
            }

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
            String previousBidderId = (highestBidderId == null || highestBidderId.isBlank() || highestBidderId.equals("none"))
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

        boolean hasBid = highestBidderId != null
                && !highestBidderId.isBlank()
                && !highestBidderId.equals("none");

        if (!hasBid) {
            log.info("입찰자 없음 - auctionId: {}", auctionId);
            return null;
        }

        log.info("최고가 조회 완료 - auctionId: {}, winnerId: {}, finalPrice: {}", auctionId, highestBidderId, currentPrice);
        return new HighestBidResponse(UUID.fromString(highestBidderId), Integer.parseInt(currentPrice));
    }
}
