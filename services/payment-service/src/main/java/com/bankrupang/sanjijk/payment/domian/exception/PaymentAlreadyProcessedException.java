package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class PaymentAlreadyProcessedException extends BaseException {
  public PaymentAlreadyProcessedException() {
    super(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED);
  }
}
