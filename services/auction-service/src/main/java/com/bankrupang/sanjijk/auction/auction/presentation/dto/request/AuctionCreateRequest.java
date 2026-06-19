package com.bankrupang.sanjijk.auction.auction.presentation.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AuctionCreateRequest(

        @NotNull(message = "상품 ID는 필수입니다.")
        UUID productId,

        @NotNull(message = "시작가는 필수입니다.")
        @Positive(message = "시작가는 1 이상이어야 합니다.")
        Integer startPrice,

        @NotNull(message = "입찰 단위는 필수입니다.")
        @Positive(message = "입찰 단위는 1 이상이어야 합니다.")
        Integer bidUnit,

        @NotNull(message = "경매 시작 시각은 필수입니다.")
        LocalDateTime startAt

) {

}
