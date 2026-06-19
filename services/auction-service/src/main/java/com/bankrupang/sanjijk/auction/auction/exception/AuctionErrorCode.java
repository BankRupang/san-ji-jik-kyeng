package com.bankrupang.sanjijk.auction.auction.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum AuctionErrorCode implements ErrorCode {

    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "AUCTION-001", "경매를 찾을 수 없습니다."),
    INVALID_STATE_TRANSITION(HttpStatus.BAD_REQUEST, "AUCTION-002", "유효하지 않은 경매 상태 변경입니다."),
    AUCTION_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "AUCTION-003", "수정할 수 없는 경매 상태입니다."),
    AUCTION_FORBIDDEN(HttpStatus.FORBIDDEN, "AUCTION-004", "경매에 대한 권한이 없습니다."),
    INVALID_AUCTION_PERIOD(HttpStatus.BAD_REQUEST, "AUCTION-005", "경매 시간이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

}
