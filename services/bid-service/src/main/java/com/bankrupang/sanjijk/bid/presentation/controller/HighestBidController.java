package com.bankrupang.sanjijk.bid.presentation.controller;

import com.bankrupang.sanjijk.bid.application.service.BidService;
import com.bankrupang.sanjijk.bid.presentation.dto.HighestBidResponse;
import com.bankrupang.sanjijk.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bids")
@RequiredArgsConstructor
public class HighestBidController {

    private final BidService bidService;

    @GetMapping("/auctions/{auctionId}/highest")
    public HighestBidResponse getHighestBid(@PathVariable UUID auctionId) {
        HighestBidResponse response = bidService.getHighestBid(auctionId);
        return response;
    }
}
