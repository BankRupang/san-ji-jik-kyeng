package com.bankrupang.sanjijk.auction.product.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "상품 설명은 필수입니다.")
        @Size(max = 500, message = "상품 설명은 500자 이하여야 합니다.")
        String description,

        @NotBlank(message = "상품 수량은 필수입니다.")
        @Size(max = 50, message = "상품 수량은 50자 이하여야 합니다.")
        String quantity
) {

}
