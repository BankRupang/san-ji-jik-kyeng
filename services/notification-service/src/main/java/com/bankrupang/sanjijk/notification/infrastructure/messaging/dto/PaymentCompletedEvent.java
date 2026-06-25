package com.bankrupang.sanjijk.notification.infrastructure.messaging.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class PaymentCompletedEvent {

    private UUID orderId;
    private UUID auctionId;
    private String auctionTitle;
    private UUID winnerId;
    private UUID sellerId;
    private Integer finalPrice;
    private int paidAmount;
    private String paymentType;
    private LocalDateTime occurredAt;
}
