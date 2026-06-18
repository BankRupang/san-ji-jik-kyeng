package com.bankrupang.sanjijk.bid.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BidRequestDto {

    private Long bidPrice;

    private Long clientSeenPrice;

    private ActionType actionType = ActionType.BID;

    public enum ActionType {
        BID,
        EXTEND
    }
}
