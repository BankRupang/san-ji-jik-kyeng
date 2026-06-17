package com.bankrupang.sanjijk.auction.product.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProductCreateRequest(

        @NotBlank
        String name,

        @NotBlank
        String description,

        @NotBlank
        String quantity
) {

}
