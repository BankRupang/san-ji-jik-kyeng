package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

// OrderEventPublishFailedException.java
public class OrderEventPublishFailedException extends BaseException {
    public OrderEventPublishFailedException() {
        super(OrderErrorCode.ORDER_EVENT_PUBLISH_FAILED);
    }
}
