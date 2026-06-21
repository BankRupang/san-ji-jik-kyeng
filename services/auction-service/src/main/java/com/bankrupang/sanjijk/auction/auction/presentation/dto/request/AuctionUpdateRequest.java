package com.bankrupang.sanjijk.auction.auction.presentation.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;

public record AuctionUpdateRequest(

        @Positive(message = "시작가는 1 이상이어야 합니다.")
        Integer startPrice,

        @Positive(message = "입찰 단위는 1 이상이어야 합니다.")
        Integer bidUnit,

        @Future(message = "경매 시작 시각은 현재 시각 이후여야 합니다.")
        LocalDateTime startAt
) {

}
