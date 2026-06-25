package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class OrderNotFoundException extends BaseException {
    public OrderNotFoundException() {
        super(OrderErrorCode.ORDER_NOT_FOUND);
    }
}
