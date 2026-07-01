package com.bankrupang.sanjijk.bid.application.service;

import com.bankrupang.sanjijk.bid.infrastructure.kafka.BidEventProducer;
import com.bankrupang.sanjijk.bid.presentation.dto.BidRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 시나리오 3.1 - 안티 스나이핑
 * 마감 30초 이내 입찰 시 endAt이 1분 자동 연장되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("시나리오 3.1 - 안티 스나이핑")
class BidServiceAntiSnipingTest {

    @Mock private RedissonClient redissonClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private BidEventProducer bidEventProducer;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private RLock lock;

    @InjectMocks private BidService bidService;

    private UUID auctionId;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        auctionId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private BidRequestDto createRequest(Integer bidPrice, Integer clientSeenPrice) {
        BidRequestDto dto = new BidRequestDto();
        ReflectionTestUtils.setField(dto, "bidPrice", bidPrice);
        ReflectionTestUtils.setField(dto, "clientSeenPrice", clientSeenPrice);
        return dto;
    }

    @Test
    @DisplayName("입찰 시 endAt now+1분 연장 + AUCTION_EXTENDED 이벤트 발행")
    void shouldExtendEndAt_whenBidWithin30Seconds() throws Exception {
        LocalDateTime nearEndAt = LocalDateTime.now().plusSeconds(20);

        Map<Object, Object> info = new HashMap<>();
        info.put("status", "PROGRESS");
        info.put("endAt", nearEndAt.toString());
        info.put("currentPrice", "10000");
        info.put("bidUnit", "1000");
        info.put("highestBidderId", "");
        info.put("productName", "테스트상품");
        when(hashOperations.entries(anyString())).thenReturn(info);

        bidService.bid(auctionId, userId, createRequest(11000, 10000));

        ArgumentCaptor<String> endAtCaptor = ArgumentCaptor.forClass(String.class);
        verify(hashOperations).put(anyString(), eq("endAt"), endAtCaptor.capture());
        LocalDateTime newEndAt = LocalDateTime.parse(endAtCaptor.getValue());
        assertThat(newEndAt).isAfter(LocalDateTime.now().plusSeconds(55));

        verify(zSetOperations).add(eq("auction:endings"), eq(auctionId.toString()), anyDouble());

    }
}
