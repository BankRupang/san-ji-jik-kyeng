package com.bankrupang.sanjijk.bid.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class HighestBidResponse {
    private UUID winnerId;
    private Integer finalPrice;
}
