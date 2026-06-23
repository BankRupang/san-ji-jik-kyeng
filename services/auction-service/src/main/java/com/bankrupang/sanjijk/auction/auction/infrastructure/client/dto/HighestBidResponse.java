package com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto;

import java.util.UUID;

public record HighestBidResponse(
        UUID winnerId,
        Integer finalPrice
) {
}
