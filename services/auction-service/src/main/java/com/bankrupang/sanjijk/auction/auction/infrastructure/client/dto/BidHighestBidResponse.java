package com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto;

import java.util.UUID;

public record BidHighestBidResponse(
        UUID winnerId,
        Long finalPrice
) {
}
