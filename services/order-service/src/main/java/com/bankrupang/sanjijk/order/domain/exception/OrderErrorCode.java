package com.bankrupang.sanjijk.order.domain.exception;

import com.bankrupang.sanjijk.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-001", "주문을 찾을 수 없습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER-002", "주문에 대한 접근 권한이 없습니다."),
    DUPLICATE_ORDER(HttpStatus.CONFLICT, "ORDER-003", "이미 보증금 주문이 존재합니다."),

    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "ORDER-100", "유효하지 않은 주문 상태 전이입니다."),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "ORDER-101", "이미 결제 완료된 주문입니다."),
    ORDER_PAYMENT_EXPIRED(HttpStatus.BAD_REQUEST, "ORDER-102", "결제 기한이 만료된 주문입니다."),
    ORDER_CONCURRENT_UPDATE(HttpStatus.CONFLICT, "ORDER-103", "동시 요청으로 인해 주문 처리에 실패했습니다."),

    ORDER_EVENT_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER-900", "주문 이벤트 발행 중 오류가 발생했습니다."),
    ORDER_SAGA_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER-901", "주문 처리 중 오류가 발생했습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}
