package com.bankrupang.sanjijk.payment.domian.entity;

import com.bankrupang.sanjijk.common.entity.BaseEntity;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import com.bankrupang.sanjijk.payment.domian.exception.InvalidPaymentStatusException;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "seller_id")
    private UUID sellerId;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "auction_title", nullable = false, length = 30)
    private String auctionTitle;

    @Column(name = "toss_order_id", nullable = false, unique = true, length = 64)
    private String tossOrderId;

    @Column(name = "payment_key", nullable = false, unique = true, length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "original_amount")
    private Integer originalAmount;

    @Column(name = "card_issuer_code", length = 2)
    private String cardIssuerCode;

    @Column(name = "card_number", length = 20)
    private String cardNumber;

    @Column(name = "card_type", length = 10)
    private String cardType;

    @Column(name = "installment_months", columnDefinition = "int default 0")
    private Integer installmentMonths;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 510)
    private String failureMessage;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancel_amount")
    private int cancelAmount;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    // ================================
    // 팩토리 메서드
    // ================================

    public static Payment create(
            UUID orderId,
            UUID userId,
            UUID sellerId,
            UUID auctionId,
            String auctionTitle,
            String tossOrderId,
            PaymentType paymentType,
            int amount,
            Integer originalAmount
    ) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.sellerId = sellerId;
        payment.auctionId = auctionId;
        payment.auctionTitle = auctionTitle;
        payment.tossOrderId = tossOrderId;
        payment.paymentKey = "";       // 토스 confirm 응답 전 빈 문자열
        payment.paymentType = paymentType;
        payment.status = PaymentStatus.READY;
        payment.amount = amount;
        payment.originalAmount = originalAmount;
        payment.installmentMonths = 0;
        payment.requestedAt = LocalDateTime.now();
        return payment;
    }

    // ================================
    // 상태 전이 메서드
    // ================================

    public void confirm(
            String paymentKey,
            String cardIssuerCode,
            String cardNumber,
            String cardType,
            Integer installmentMonths,
            String receiptUrl,
            LocalDateTime approvedAt
    ) {
        validateStatus(PaymentStatus.IN_PROGRESS);
        this.paymentKey = paymentKey;
        this.cardIssuerCode = cardIssuerCode;
        this.cardNumber = cardNumber;
        this.cardType = cardType;
        this.installmentMonths = installmentMonths;
        this.receiptUrl = receiptUrl;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.DONE;
    }

    public void fail(String failureCode, String failureMessage) {
        if (this.status != PaymentStatus.READY && this.status != PaymentStatus.IN_PROGRESS) {
            throw new InvalidPaymentStatusException();
        }
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.status = PaymentStatus.ABORTED;
    }

    public void expire() {
        validateStatus(PaymentStatus.READY);
        this.status = PaymentStatus.EXPIRED;
    }

    public void cancel(int cancelAmount, String cancelReason) {
        this.cancelAmount = cancelAmount;
        this.cancelReason = cancelReason;
        this.canceledAt = LocalDateTime.now();
    }

    public void inProgress() {
        validateStatus(PaymentStatus.READY);
        this.status = PaymentStatus.IN_PROGRESS;
    }

    private void validateStatus(PaymentStatus required) {
        if (this.status != required) {
            throw new InvalidPaymentStatusException();
        }
    }
}