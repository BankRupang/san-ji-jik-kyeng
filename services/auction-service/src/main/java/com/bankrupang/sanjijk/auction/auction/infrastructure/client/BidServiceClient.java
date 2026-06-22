package com.bankrupang.sanjijk.auction.auction.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto.BidHighestBidResponse;

@FeignClient(name = "bid-service")
public interface BidServiceClient {

    // TODO: bid-service에서 최고가/최고입찰자 조회 API 제공 후 마감 fallback 로직에 연결한다.
    @GetMapping("/api/v1/bids/auctions/{auctionId}/highest")
    BidHighestBidResponse getHighestBid(@PathVariable UUID auctionId);
}
