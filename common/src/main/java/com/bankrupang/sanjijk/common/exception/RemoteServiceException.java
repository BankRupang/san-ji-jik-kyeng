package com.bankrupang.sanjijk.common.exception;

import org.springframework.http.HttpStatus;

public class RemoteServiceException extends BaseException {

    public RemoteServiceException(int httpStatus, String code, String message) {
        super(new RemoteErrorCode(
                HttpStatus.resolve(httpStatus) != null ? HttpStatus.resolve(httpStatus) : HttpStatus.INTERNAL_SERVER_ERROR,
                code, message));
    }

    private record RemoteErrorCode(
            HttpStatus status, String code, String message
    ) implements ErrorCode {
        @Override public HttpStatus getStatus() { return status; }
        @Override public String getCode() { return code; }
        @Override public String getMessage() { return message; }
    }
}
