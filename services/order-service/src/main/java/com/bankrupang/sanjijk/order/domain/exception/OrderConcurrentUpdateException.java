package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

// OrderConcurrentUpdateException.java
public class OrderConcurrentUpdateException extends BaseException {
    public OrderConcurrentUpdateException() {
        super(OrderErrorCode.ORDER_CONCURRENT_UPDATE);
    }
}
