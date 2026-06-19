package com.bankrupang.sanjijk.auction.product.presentation.dto.request;

import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(

        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String name,

        @Size(max = 500, message = "상품 설명은 500자 이하여야 합니다.")
        String description,

        @Size(max = 50, message = "상품 수량은 50자 이하여야 합니다.")
        String quantity
) {

}
