package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class InvalidPaymentStatusException extends BaseException {
    public InvalidPaymentStatusException() {
        super(PaymentErrorCode.INVALID_PAYMENT_STATUS);
    }
}
