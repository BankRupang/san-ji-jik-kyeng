package com.bankrupang.sanjijk.auction.product.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT-001", "상품을 찾을 수 없습니다."),
    PRODUCT_FORBIDDEN(HttpStatus.FORBIDDEN, "PRODUCT-002", "상품을 등록할 권한이 없습니다."),
    INVALID_PRODUCT_REQUEST(HttpStatus.BAD_REQUEST, "PRODUCT-003", "수정할 상품 정보를 입력해주세요."),
    PRODUCT_NOT_EDITABLE(HttpStatus.CONFLICT, "PRODUCT-004", "경매가 진행 중이거나 종료된 상품은 수정하거나 삭제할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

}
