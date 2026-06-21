package com.bankrupang.sanjijk.bid.infrastructure.scheduler;

import com.bankrupang.sanjijk.bid.domain.event.AuctionEndedEvent;
import com.bankrupang.sanjijk.bid.infrastructure.kafka.BidEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEndScheduler {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final BidEventProducer bidEventProducer;

    @Scheduled(fixedDelay = 10000)
    public void checkAuctionEndings() {
        long now = Instant.now().getEpochSecond();

        Set<String> endedAuctions = redisTemplate.opsForZSet()
                .rangeByScore("auction:endings", 0, now);

        if (endedAuctions == null || endedAuctions.isEmpty()) return;

        for (String auctionId : endedAuctions) {
            String lockKey = "auction:" + auctionId + ":end-lock";
            RLock lock = redissonClient.getLock(lockKey);

            if (lock.tryLock()) {
                try {
                    log.info("경매 종료 처리 - auctionId: {}", auctionId);

                    // WebSocket으로 종료 메시지 전송
                    messagingTemplate.convertAndSend(
                            "/topic/auction/" + auctionId,
                            "{\"type\":\"AUCTION_ENDED\"}"
                    );

                    // Kafka AUCTION_ENDED 이벤트 발행
                    Map<Object, Object> info = redisTemplate.opsForHash()
                            .entries("auction:" + auctionId + ":info");
                    String highestBidderId = (String) info.get("highestBidderId");
                    String currentPrice = (String) info.get("currentPrice");
                    boolean hasBid = highestBidderId != null && !highestBidderId.isBlank() && !highestBidderId.equals("none");

                    AuctionEndedEvent endedEvent = new AuctionEndedEvent(
                            auctionId,
                            hasBid,
                            hasBid ? highestBidderId : null,
                            hasBid ? Long.parseLong(currentPrice) : null,
                            LocalDateTime.now()
                    );
                    bidEventProducer.sendAuctionEnded(endedEvent);

                    redisTemplate.opsForZSet().remove("auction:endings", auctionId);

                    log.info("경매 종료 완료 - auctionId: {}", auctionId);
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
