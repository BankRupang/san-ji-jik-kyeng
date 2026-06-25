package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

// OrderSagaFailedException.java
public class OrderSagaFailedException extends BaseException {
    public OrderSagaFailedException() {
        super(OrderErrorCode.ORDER_SAGA_FAILED);
    }
}
