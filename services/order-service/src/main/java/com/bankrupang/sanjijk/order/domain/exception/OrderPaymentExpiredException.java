package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

// OrderPaymentExpiredException.java
public class OrderPaymentExpiredException extends BaseException {
    public OrderPaymentExpiredException() {
        super(OrderErrorCode.ORDER_PAYMENT_EXPIRED);
    }
}
