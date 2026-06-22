package com.bankrupang.sanjijk.payment.domian.exception;

import com.bankrupang.sanjijk.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-001", "결제 내역을 찾을 수 없습니다."),

    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "PAYMENT-100", "유효하지 않은 결제 상태 전이입니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PAYMENT-101", "이미 처리된 결제입니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT-102", "결제 금액이 일치하지 않습니다."),

    TOSS_PAYMENT_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT-200", "토스페이먼츠 API 호출에 실패했습니다."),

    PAYMENT_EVENT_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT-900", "결제 이벤트 발행 중 오류가 발생했습니다."),
    PAYMENT_SAGA_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT-901", "결제 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
