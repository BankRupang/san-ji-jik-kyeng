package com.bankrupang.sanjijk.bid.application.service;

import com.bankrupang.sanjijk.bid.domain.exception.BidException;
import com.bankrupang.sanjijk.bid.infrastructure.kafka.BidEventProducer;
import com.bankrupang.sanjijk.bid.presentation.dto.BidRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 시나리오 2.1 - 대규모 동시 입찰 시 단일 최고가 보장
 *
 * ⚠️ 통합 테스트 - 로컬 Redis(localhost:6379) 필요
 * 실행: ./gradlew :bid-service:test -Dgroups=integration
 */
@Tag("integration")
@DisplayName("시나리오 2.1 - 동시성 테스트 (Redis 필요)")
class BidConcurrencyTest {

    private static final UUID AUCTION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String HASH_KEY = "auction:" + AUCTION_ID + ":info";

    private BidService bidService;
    private StringRedisTemplate redisTemplate;
    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        BidEventProducer bidEventProducer = mock(BidEventProducer.class);
        doNothing().when(bidEventProducer).sendBidOvertaken(any());

        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        redissonClient = Redisson.create(config);

        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        bidService = new BidService(redissonClient, redisTemplate, objectMapper, bidEventProducer);

        // 경매 초기 상태 세팅
        redisTemplate.delete(HASH_KEY);
        redisTemplate.opsForHash().put(HASH_KEY, "status", "PROGRESS");
        redisTemplate.opsForHash().put(HASH_KEY, "endAt", LocalDateTime.now().plusHours(1).toString());
        redisTemplate.opsForHash().put(HASH_KEY, "currentPrice", "10000");
        redisTemplate.opsForHash().put(HASH_KEY, "bidUnit", "1000");
        redisTemplate.opsForHash().put(HASH_KEY, "highestBidderId", "");
        redisTemplate.opsForHash().put(HASH_KEY, "productName", "테스트상품");
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(HASH_KEY);
        redisTemplate.delete("auction:endings");
        redissonClient.shutdown();
    }

    @Test
    @DisplayName("100명 동시 입찰 시 단 1건만 성공, 최고가 1회만 갱신")
    void concurrent_100bids_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);  // 동시 시작 신호
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            UUID userId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 출발
                    BidRequestDto request = new BidRequestDto();
                    ReflectionTestUtils.setField(request, "bidPrice", 11000);
                    ReflectionTestUtils.setField(request, "clientSeenPrice", 10000);
                    bidService.bid(AUCTION_ID, userId, request);
                    successCount.incrementAndGet();
                } catch (BidException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 시작
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // 핵심 검증: 단 1건만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(99);

        // Redis 최종 상태 검증: currentPrice 1회만 갱신
        String finalPrice = (String) redisTemplate.opsForHash().get(HASH_KEY, "currentPrice");
        assertThat(finalPrice).isEqualTo("11000");

        // highestBidderId가 정상적으로 1명만 설정되었는지
        String highestBidderId = (String) redisTemplate.opsForHash().get(HASH_KEY, "highestBidderId");
        assertThat(highestBidderId).isNotBlank();
    }
}
