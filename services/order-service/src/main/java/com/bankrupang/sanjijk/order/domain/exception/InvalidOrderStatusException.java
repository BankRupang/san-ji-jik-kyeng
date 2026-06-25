package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

// InvalidOrderStatusException.java
public class InvalidOrderStatusException extends BaseException {
    public InvalidOrderStatusException() {
        super(OrderErrorCode.INVALID_ORDER_STATUS);
    }
}
