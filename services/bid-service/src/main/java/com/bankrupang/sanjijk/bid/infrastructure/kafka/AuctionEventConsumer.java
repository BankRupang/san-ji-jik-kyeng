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
        AuctionStartedEvent event;
        try {
            event = objectMapper.readValue(message, AuctionStartedEvent.class);
        } catch (Exception e) {
            log.error("auction-start 이벤트 역직렬화 실패: {}", e.getMessage());
            return;
        }
        log.info("경매 시작 이벤트 수신 - auctionId: {}", event.getAuctionId());

        String auctionId = event.getAuctionId().toString();
        LocalDateTime endAt = event.getStartAt().plusHours(1);
        long ttlSeconds = Duration.between(LocalDateTime.now(), endAt).getSeconds();

        if (ttlSeconds <= 0) {
            log.warn("경매가 이미 종료됨, Redis 저장 스킵 - auctionId: {}, endAt: {}", auctionId, endAt);
            return;
        }

        String hashKey = "auction:" + auctionId + ":info";

        Map<String, String> auctionInfo = new HashMap<>();
        auctionInfo.put("productName", event.getProductName());
        auctionInfo.put("currentPrice", String.valueOf(event.getStartPrice()));
        auctionInfo.put("bidUnit", String.valueOf(event.getBidUnit()));
        auctionInfo.put("startAt", event.getStartAt().toString());
        auctionInfo.put("endAt", endAt.toString());
        auctionInfo.put("status", event.getStatus());
        auctionInfo.put("highestBidderId", "");

        try {
            redisTemplate.opsForHash().putAll(hashKey, auctionInfo);
            redisTemplate.expire(hashKey, Duration.ofSeconds(ttlSeconds));

            // 경매 종료 스케줄링을 위해 Sorted Set에 등록 (score = endAt Unix timestamp)
            redisTemplate.opsForZSet().add("auction:endings", auctionId, endAt.toEpochSecond(java.time.ZoneOffset.UTC));

            log.info("Redis 경매 정보 저장 완료 - auctionId: {}, endAt: {}", auctionId, endAt);
        } catch (Exception e) {
            log.error("auction-start Redis 저장 실패 - auctionId: {}, 메시지를 스킵합니다. error: {}", auctionId, e.getMessage());
        }
    }
}
