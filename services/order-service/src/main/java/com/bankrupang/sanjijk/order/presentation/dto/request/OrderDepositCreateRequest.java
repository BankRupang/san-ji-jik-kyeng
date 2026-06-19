package com.bankrupang.sanjijk.order.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderDepositCreateRequest(
        @NotNull(message = "경매 방 아이디는 필수 입니다.")
        UUID auctionId,

        @NotBlank(message = "경매 제목은 필수입니다.")
        String auctionTitle,

        @NotNull(message = "보증금 금액은 필수입니다.")
        @Positive(message = "보증금 금액은 0보다 커야합니다.")
        int amount,

        @NotBlank(message = "유저 이름은 필수입니다.")
        String userName,

        @NotBlank(message = "슬랙 아이디는 필수입니다.")
        String slackId
) {
}
