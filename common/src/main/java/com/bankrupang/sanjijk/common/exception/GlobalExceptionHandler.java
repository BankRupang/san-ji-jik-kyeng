package com.bankrupang.sanjijk.common.exception;

import com.bankrupang.sanjijk.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
        log.warn("BaseException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse(CommonErrorCode.INVALID_INPUT.getMessage());
        log.warn("ValidationException: {}", message);
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT.name(), message));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(RuntimeException e) {
        log.warn("AccessDeniedException: {}", e.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.FORBIDDEN.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("TypeMismatchException: {}", e.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT.name(),
                        "잘못된 형식의 값입니다: " + e.getValue()));
    }
}
