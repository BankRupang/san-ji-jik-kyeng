package com.bankrupang.sanjijk.payment.infrastructure.external.dto.response;

import java.util.List;

public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        int totalAmount,
        Card card,
        Failure failure,
        List<Cancel> cancels,
        String approvedAt,
        Receipt receipt
) {
    public record Card(
            String issuerCode,
            String number,
            String cardType,
            int installmentPlanMonths
    ){}

    public record Failure(
      String code,
      String message
    ){}

    // toss에선 cancel == refund
    public record Cancel(
        int cancelAmount,
        String cancelReason,
        String canceledAt
    ){}

    public record Receipt(
            String url
    ){}
}
