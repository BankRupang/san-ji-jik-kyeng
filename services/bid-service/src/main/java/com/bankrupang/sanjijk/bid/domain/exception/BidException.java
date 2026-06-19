package com.bankrupang.sanjijk.bid.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class BidException extends BaseException {

    public BidException(BidErrorCode errorCode) {
        super(errorCode);
    }
}
