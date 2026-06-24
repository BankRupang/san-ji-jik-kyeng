package com.bankrupang.sanjijk.order.infrastructure.feign.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionInfoResponse(
        UUID auctionId,
        String auctionTitle,
        int depositAmount,
        LocalDateTime endAt
) {
}
