package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class PaymentNotFoundException extends BaseException {
  public PaymentNotFoundException() {
    super(PaymentErrorCode.PAYMENT_NOT_FOUND);
  }
}
