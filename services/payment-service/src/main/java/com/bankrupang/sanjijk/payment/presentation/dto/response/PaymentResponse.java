package com.bankrupang.sanjijk.payment.presentation.dto.response;

import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        UUID auctionId,
        String auctionTitle,
        PaymentType paymentType,
        PaymentStatus status,
        int amount,
        String paymentKey,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAuctionId(),
                payment.getAuctionTitle(),
                payment.getPaymentType(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaymentKey(),
                payment.getRequestedAt(),
                payment.getApprovedAt()
        );
    }
}
