package com.bankrupang.sanjijk.bid.infrastructure.kafka;

import com.bankrupang.sanjijk.bid.domain.event.AuctionStartedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventConsumer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auction-start", groupId = "bid-service")
    public void handleAuctionStarted(String message) {
        try {
            AuctionStartedEvent event = objectMapper.readValue(message, AuctionStartedEvent.class);
            log.info("경매 시작 이벤트 수신 - auctionId: {}", event.getAuctionId());

            String auctionId = event.getAuctionId().toString();
            LocalDateTime endAt = event.getStartAt().plusHours(1);
            long ttlSeconds = Duration.between(LocalDateTime.now(), endAt).getSeconds();

            if (ttlSeconds <= 0) {
                log.warn("경매가 이미 종료됨, Redis 저장 스킵 - auctionId: {}, endAt: {}", auctionId, endAt);
                return;
            }

            String hashKey = "auction:" + auctionId + ":info";

            // 멱등성: 이미 처리된 경매면 스킵
            Boolean exists = redisTemplate.hasKey(hashKey);
            if (Boolean.TRUE.equals(exists)) {
                log.info("이미 처리된 경매 이벤트 스킵 - auctionId: {}", auctionId);
                return;
            }

            Map<String, String> auctionInfo = new HashMap<>();
            auctionInfo.put("productName", event.getProductName());
            auctionInfo.put("currentPrice", String.valueOf(event.getStartPrice()));
            auctionInfo.put("bidUnit", String.valueOf(event.getBidUnit()));
            auctionInfo.put("startAt", event.getStartAt().toString());
            auctionInfo.put("endAt", endAt.toString());
            auctionInfo.put("status", event.getStatus());
            auctionInfo.put("highestBidderId", "");

        redisTemplate.opsForHash().putAll(hashKey, auctionInfo);
        redisTemplate.expire(hashKey, Duration.ofSeconds(ttlSeconds));
        redisTemplate.opsForZSet().add("auction:endings", auctionId, endAt.toEpochSecond(java.time.ZoneOffset.UTC));

            log.info("Redis 경매 정보 저장 완료 - auctionId: {}, endAt: {}", auctionId, endAt);

        } catch (Exception e) {
            // [수정됨] Throwable 대신 Exception만 잡도록 수정했습니다.
            // 위에서 RedisConfig를 수정했으므로 더 이상 StackOverflowError는 나지 않을 것입니다.
            log.error("auction-start 처리 실패 - 메시지 스킵. error: {}, message: {}", e.getMessage(), message, e);
        }
    }
}