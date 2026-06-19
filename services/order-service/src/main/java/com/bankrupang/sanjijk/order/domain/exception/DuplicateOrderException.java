package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class DuplicateOrderException extends BaseException {
    public DuplicateOrderException() {super(OrderErrorCode.DUPLICATE_ORDER);}
}
