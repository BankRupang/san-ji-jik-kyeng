package com.bankrupang.sanjijk.auction.auction.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class AuctionException extends BaseException {

    public AuctionException(AuctionErrorCode errorCode) {
        super(errorCode);
    }

}
