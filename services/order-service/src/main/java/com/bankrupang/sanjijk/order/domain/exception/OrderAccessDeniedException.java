package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

// OrderAccessDeniedException.java
public class OrderAccessDeniedException extends BaseException {
    public OrderAccessDeniedException() {
        super(OrderErrorCode.ORDER_ACCESS_DENIED);
    }
}
