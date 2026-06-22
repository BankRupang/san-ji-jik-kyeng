package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class PaymentSagaFailedException extends BaseException {
  public PaymentSagaFailedException() {
    super(PaymentErrorCode.PAYMENT_SAGA_FAILED);
  }
}
