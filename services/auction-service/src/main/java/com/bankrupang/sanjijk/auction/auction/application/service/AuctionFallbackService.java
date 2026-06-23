package com.bankrupang.sanjijk.auction.auction.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bankrupang.sanjijk.auction.auction.infrastructure.client.BidClient;
import com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto.HighestBidResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionFallbackService {

    private final BidClient bidClient;

    public HighestBidResponse getHighestBidWithRetry(UUID auctionId) {
        int maxAttempts = 3;
        long delayMs = 1000L;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("bid-service 최고가 조회 시도 - attempt: {}/{}", attempt, maxAttempts);
                HighestBidResponse response = bidClient.getHighestBid(auctionId);
                log.info("bid-service 최고가 조회 성공 - winnerId: {}, finalPrice: {}",
                        response != null ? response.winnerId() : null,
                        response != null ? response.finalPrice() : null);
                return response;
            } catch (Exception e) {
                lastException = e;
                log.warn("bid-service 최고가 조회 실패 - attempt: {}/{}, error: {}",
                        attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("재시도 대기 중 인터럽트 발생", ie);
                    }
                }
            }
        }

        throw new RuntimeException("bid-service 최고가 조회 최종 실패 (3회 시도)", lastException);
    }
}
