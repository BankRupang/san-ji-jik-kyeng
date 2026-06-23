package com.bankrupang.sanjijk.auction.auction.presentation.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Positive;

public record AuctionCloseRequest(

        UUID winnerId,

        @Positive(message = "최종 낙찰가는 1 이상이어야 합니다.")
        Integer finalPrice

) {

}
