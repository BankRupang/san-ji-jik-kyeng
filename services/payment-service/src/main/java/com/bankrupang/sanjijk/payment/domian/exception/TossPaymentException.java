package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;
import lombok.Getter;

@Getter
public class TossPaymentException extends BaseException {

    private final String code;

    public TossPaymentException() {
        super(PaymentErrorCode.TOSS_PAYMENT_FAILED);
        this.code = null;
    }

    public TossPaymentException(String code, String message) {
        super(PaymentErrorCode.TOSS_PAYMENT_FAILED, code + " : " + message);
        this.code = code;
    }
}
