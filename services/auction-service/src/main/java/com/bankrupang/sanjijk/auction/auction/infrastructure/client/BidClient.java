package com.bankrupang.sanjijk.auction.auction.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto.HighestBidResponse;

@FeignClient(name = "bid-service")
public interface BidClient {

    @GetMapping("/api/v1/bids/auctions/{auctionId}/highest")
    HighestBidResponse getHighestBid(@PathVariable("auctionId") UUID auctionId);
}
