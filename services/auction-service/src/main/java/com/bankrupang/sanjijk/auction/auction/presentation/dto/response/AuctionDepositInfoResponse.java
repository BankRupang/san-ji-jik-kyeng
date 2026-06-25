package com.bankrupang.sanjijk.auction.auction.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionDepositInfoResponse(
        UUID auctionId,
        int depositAmount,
        String auctionTitle,
        LocalDateTime endAt
) {}
