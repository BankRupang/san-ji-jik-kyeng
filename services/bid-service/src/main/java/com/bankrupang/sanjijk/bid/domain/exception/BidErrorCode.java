package com.bankrupang.sanjijk.bid.domain.exception;

import com.bankrupang.sanjijk.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BidErrorCode implements ErrorCode {

    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경매입니다."),
    AUCTION_NOT_IN_PROGRESS(HttpStatus.BAD_REQUEST, "진행 중인 경매가 아닙니다."),
    AUCTION_ENDED(HttpStatus.BAD_REQUEST, "종료된 경매입니다."),
    BID_PRICE_OUTDATED(HttpStatus.CONFLICT, "현재 입찰가가 변경되었습니다. 새로고침 후 다시 시도해주세요."),
    ALREADY_HIGHEST_BIDDER(HttpStatus.BAD_REQUEST, "이미 최고 입찰자입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
