package com.bankrupang.sanjijk.auction.auction.presentation.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Positive;

public record AuctionCloseRequest(

        Boolean forceFail

) {
    public AuctionCloseRequest {
        if (forceFail == null) {
            forceFail = false;
        }
    }
}
