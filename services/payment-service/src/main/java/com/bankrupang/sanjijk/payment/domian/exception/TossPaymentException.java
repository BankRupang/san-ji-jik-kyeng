package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class TossPaymentException extends BaseException {
  public TossPaymentException() {
    super(PaymentErrorCode.TOSS_PAYMENT_FAILED);
  }

  public TossPaymentException(String code, String message) {
    super(PaymentErrorCode.TOSS_PAYMENT_FAILED, code + " : " + message);
  }
}
