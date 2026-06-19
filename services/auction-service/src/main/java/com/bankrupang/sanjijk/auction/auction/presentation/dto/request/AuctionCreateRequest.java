package com.bankrupang.sanjijk.auction.auction.presentation.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AuctionCreateRequest(

        @NotNull
        UUID productId,

        @NotNull
        @Positive
        Integer startPrice,

        @NotNull
        @Positive
        Integer bidUnit,

        @NotNull
        LocalDateTime startAt,

        @NotNull
        LocalDateTime endAt
) {

}
