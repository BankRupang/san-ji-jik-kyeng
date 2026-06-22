package com.bankrupang.sanjijk.payment.infrastructure.external.dto.response;

public record TossErrorResponse(
        String code,
        String message
) {
}
