package com.bankrupang.sanjijk.payment.presentation.dto.request;

import java.util.UUID;

public record PaymentConfirmRequest(
        String paymentKey,    // TossPayments 발급 결제 키
        String tossOrderId,   // TossPayments orderId (= 우리 orderId.toString())
        int amount,           // 결제 금액
        UUID auctionId        // 프론트에서 전달
) {}
