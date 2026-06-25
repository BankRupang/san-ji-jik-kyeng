package com.bankrupang.sanjijk.payment.infrastructure.external.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TossCancelRequest(
        // null이면 전액취소(=환불), 값 있으면 부분 취소
        // TossPayments 환불 = 취소(cancel) API
        // @JsonInclude(NON_NULL)로 null 필드는 직렬화 제외 → TossPayments가 전액취소로 처리
        String cancelReason,
        Integer cancelAmount
) {
}
