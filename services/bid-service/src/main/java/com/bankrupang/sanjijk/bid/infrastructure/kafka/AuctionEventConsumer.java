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
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.script.DefaultRedisScript;

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
            LocalDateTime endAt = event.getStartAt().plusMinutes(10);
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
        DefaultRedisScript<Long> expireScript = new DefaultRedisScript<>(
                "return redis.call('EXPIRE', KEYS[1], ARGV[1])", Long.class);
        redisTemplate.execute(expireScript, Collections.singletonList(hashKey), String.valueOf(ttlSeconds));
        redisTemplate.opsForZSet().add("auction:endings", auctionId, endAt.atZone(ZoneId.of("Asia/Seoul")).toEpochSecond());

        log.info("Redis 경매 정보 저장 완료 - auctionId: {}, endAt: {}", auctionId, endAt);

        } catch (Exception e) {
            log.error("auction-start 처리 실패 - 메시지 스킵. error: {}, message: {}", e.getMessage(), message, e);
        }
    }
}