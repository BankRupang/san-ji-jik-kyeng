package com.bankrupang.sanjijk.order.infrastructure.feign.dto;

import java.time.LocalDateTime;

public record AuctionInfoResponse(
        String auctionTitle,
        int depositAmount,
        LocalDateTime endAt
) {
}
