package com.bankrupang.sanjijk.order.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderDepositCreateRequest(
        @NotNull(message = "경매 방 아이디는 필수 입니다.")
        UUID auctionId
) {
}
