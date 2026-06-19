package com.bankrupang.sanjijk.ai.exception;

import com.bankrupang.sanjijk.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {

    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI-001", "채팅 세션을 찾을 수 없습니다."),
    CHAT_SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "AI-002", "만료된 채팅 세션입니다."),
    CHAT_SESSION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AI-003", "채팅 세션에 접근할 권한이 없습니다."),
    AI_RESPONSE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI-004", "AI 응답을 생성할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
