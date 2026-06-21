package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class PaymentEventPublishFailedException extends BaseException {
    public PaymentEventPublishFailedException() {
        super(PaymentErrorCode.PAYMENT_EVENT_PUBLISH_FAILED);
    }
}
