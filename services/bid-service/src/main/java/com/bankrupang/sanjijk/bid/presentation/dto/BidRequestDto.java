package com.bankrupang.sanjijk.bid.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BidRequestDto {

    private Integer bidPrice;

    private Integer clientSeenPrice;

    private ActionType actionType = ActionType.BID;

    public enum ActionType {
        BID,
        EXTEND
    }
}
