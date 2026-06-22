package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class PaymentAmountMismatchException extends BaseException {
  public PaymentAmountMismatchException() {
    super(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
  }
}
