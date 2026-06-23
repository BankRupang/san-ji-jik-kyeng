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
    AI_RESPONSE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI-004", "AI 응답을 생성할 수 없습니다."),
    DOCUMENT_INGESTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI-005", "문서 적재에 실패했습니다."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI-006", "문서를 찾을 수 없습니다."),
    DOCUMENT_EMPTY(HttpStatus.BAD_REQUEST, "AI-007", "빈 파일은 등록할 수 없습니다."),
    DOCUMENT_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "AI-008", "파일에서 텍스트 내용을 추출할 수 없습니다."),
    CHAT_SESSION_ALREADY_PROCESSING(HttpStatus.CONFLICT, "AI-009", "이미 처리 중인 요청이 있습니다."),
    LOCK_INTERRUPTED(HttpStatus.SERVICE_UNAVAILABLE, "AI-010", "처리 중 시스템 인터럽트가 발생했습니다."),
    LOCK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI-011", "락 서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
