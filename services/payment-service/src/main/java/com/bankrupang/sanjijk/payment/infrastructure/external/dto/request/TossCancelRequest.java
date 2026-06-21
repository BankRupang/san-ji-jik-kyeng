package com.bankrupang.sanjijk.payment.infrastructure.external.dto.request;

public record TossCancelRequest(
        // null이면 전액취소(=환불), 값 있으면 부분 취소
        // TossPayments 환불 = 취소(cancel) API
        String cancelReason,
        int cancelAmount
) {
}
