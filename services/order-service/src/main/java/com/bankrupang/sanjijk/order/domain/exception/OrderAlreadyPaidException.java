package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

// OrderAlreadyPaidException.java
public class OrderAlreadyPaidException extends BaseException {
    public OrderAlreadyPaidException() {
        super(OrderErrorCode.ORDER_ALREADY_PAID);
    }
}