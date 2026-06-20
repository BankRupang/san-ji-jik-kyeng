package com.bankrupang.sanjijk.auction.auction.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuctionCancelRequest(

        @NotBlank(message = "취소 사유는 필수입니다.")
        @Size(max = 100, message = "취소 사유는 100자 이하로 입력해주세요.")
        String reason

) {

}
