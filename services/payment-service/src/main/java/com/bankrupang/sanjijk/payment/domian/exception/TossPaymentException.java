package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class TossPaymentException extends BaseException {
  public TossPaymentException() {
    super(PaymentErrorCode.TOSS_PAYMENT_FAILED);
  }
}
