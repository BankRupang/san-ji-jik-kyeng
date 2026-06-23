package com.bankrupang.sanjijk.auction.auction.presentation.dto.response;

import java.time.LocalDateTime;

public record AuctionDepositInfoResponse(
        int depositAmount,
        String auctionTitle,
        LocalDateTime endAt
) {}
