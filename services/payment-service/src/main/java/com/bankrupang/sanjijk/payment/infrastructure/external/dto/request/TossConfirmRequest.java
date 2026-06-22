package com.bankrupang.sanjijk.payment.infrastructure.external.dto.request;

public record TossConfirmRequest(
        String paymentKey,
        String orderId,
        int amount
) {
}
