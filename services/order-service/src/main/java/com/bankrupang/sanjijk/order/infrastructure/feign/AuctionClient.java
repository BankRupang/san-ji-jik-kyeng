package com.bankrupang.sanjijk.order.infrastructure.feign;

import com.bankrupang.sanjijk.order.infrastructure.feign.dto.AuctionInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "auction-service")
public interface AuctionClient {
    @GetMapping("/internal/auctions/{auctionId}")
    AuctionInfoResponse getAuction(@PathVariable UUID auctionId);
}
