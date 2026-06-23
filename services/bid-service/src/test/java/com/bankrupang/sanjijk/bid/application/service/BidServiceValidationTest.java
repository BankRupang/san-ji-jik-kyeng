package com.bankrupang.sanjijk.bid.application.service;

import com.bankrupang.sanjijk.bid.domain.exception.BidErrorCode;
import com.bankrupang.sanjijk.bid.domain.exception.BidException;
import com.bankrupang.sanjijk.bid.infrastructure.kafka.BidEventProducer;
import com.bankrupang.sanjijk.bid.presentation.dto.BidRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 시나리오 1.1 - 경매 상태 및 입찰 유효성 검증
 * DB I/O 없이 Redis 상태 기반으로 8가지 비즈니스 조건을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("시나리오 1.1 - 입찰 유효성 검증")
class BidServiceValidationTest {

    @Mock private RedissonClient redissonClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private BidEventProducer bidEventProducer;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private RLock lock;

    @InjectMocks private BidService bidService;

    private UUID auctionId;
    private UUID userId;

    @BeforeEach
    void setUp() throws InterruptedException {
        auctionId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    private BidRequestDto createRequest(Integer bidPrice, Integer clientSeenPrice) {
        BidRequestDto dto = new BidRequestDto();
        ReflectionTestUtils.setField(dto, "bidPrice", bidPrice);
        ReflectionTestUtils.setField(dto, "clientSeenPrice", clientSeenPrice);
        return dto;
    }

    private Map<Object, Object> validAuctionInfo() {
        Map<Object, Object> info = new HashMap<>();
        info.put("status", "PROGRESS");
        info.put("endAt", LocalDateTime.now().plusHours(1).toString());
        info.put("currentPrice", "10000");
        info.put("bidUnit", "1000");
        info.put("highestBidderId", "none");
        info.put("productName", "테스트상품");
        return info;
    }

    @Test
    @DisplayName("경매 정보가 없으면 AUCTION_NOT_FOUND")
    void auction_notFound() {
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

        assertThatThrownBy(() -> bidService.bid(auctionId, userId, createRequest(11000, 10000)))
                .isInstanceOf(BidException.class)
                .extracting(e -> ((BidException) e).getErrorCode())
                .isEqualTo(BidErrorCode.AUCTION_NOT_FOUND);
    }

    @Test
    @DisplayName("경매 상태가 PROGRESS가 아니면 AUCTION_NOT_IN_PROGRESS")
    void auction_notInProgress() {
        Map<Object, Object> info = validAuctionInfo();
        info.put("status", "ENDED");
        when(hashOperations.entries(anyString())).thenReturn(info);

        assertThatThrownBy(() -> bidService.bid(auctionId, userId, createRequest(11000, 10000)))
                .isInstanceOf(BidException.class)
                .extracting(e -> ((BidException) e).getErrorCode())
                .isEqualTo(BidErrorCode.AUCTION_NOT_IN_PROGRESS);
    }

    @Test
    @DisplayName("마감 시간이 지났으면 AUCTION_ENDED")
    void auction_expired() {
        Map<Object, Object> info = validAuctionInfo();
        info.put("endAt", LocalDateTime.now().minusMinutes(1).toString());
        when(hashOperations.entries(anyString())).thenReturn(info);

        assertThatThrownBy(() -> bidService.bid(auctionId, userId, createRequest(11000, 10000)))
                .isInstanceOf(BidException.class)
                .extracting(e -> ((BidException) e).getErrorCode())
                .isEqualTo(BidErrorCode.AUCTION_ENDED);
    }

    @Test
    @DisplayName("clientSeenPrice가 currentPrice와 다르면 BID_PRICE_OUTDATED")
    void bid_priceOutdated() {
        when(hashOperations.entries(anyString())).thenReturn(validAuctionInfo());

        // clientSeenPrice=9000 이지만 currentPrice=10000
        assertThatThrownBy(() -> bidService.bid(auctionId, userId, createRequest(11000, 9000)))
                .isInstanceOf(BidException.class)
                .extracting(e -> ((BidException) e).getErrorCode())
                .isEqualTo(BidErrorCode.BID_PRICE_OUTDATED);
    }

    @Test
    @DisplayName("이미 최고 입찰자면 ALREADY_HIGHEST_BIDDER")
    void bid_alreadyHighestBidder() {
        Map<Object, Object> info = validAuctionInfo();
        info.put("highestBidderId", userId.toString());
        when(hashOperations.entries(anyString())).thenReturn(info);

        assertThatThrownBy(() -> bidService.bid(auctionId, userId, createRequest(11000, 10000)))
                .isInstanceOf(BidException.class)
                .extracting(e -> ((BidException) e).getErrorCode())
                .isEqualTo(BidErrorCode.ALREADY_HIGHEST_BIDDER);
    }
}
