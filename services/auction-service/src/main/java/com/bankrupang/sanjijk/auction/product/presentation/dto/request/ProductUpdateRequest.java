package com.bankrupang.sanjijk.auction.product.presentation.dto.request;

import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(

        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        @Size(max = 50)
        String quantity
) {

}
