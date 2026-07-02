package com.bankrupang.sanjijk.bid.presentation.controller;

import com.bankrupang.sanjijk.bid.application.service.BidService;
import com.bankrupang.sanjijk.bid.presentation.dto.HighestBidResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Bid", description = "입찰 API")
@RestController
@RequestMapping("/api/v1/bids")
@RequiredArgsConstructor
public class HighestBidController {

    private final BidService bidService;

    @Operation(summary = "최고 입찰 조회", description = "경매의 현재 최고 입찰자 및 최고 입찰가를 조회합니다.")
    @GetMapping("/auctions/{auctionId}/highest")
    public HighestBidResponse getHighestBid(
            @Parameter(description = "경매 ID") @PathVariable UUID auctionId) {
        return bidService.getHighestBid(auctionId);
    }
}
