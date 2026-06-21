package com.bankrupang.sanjijk.payment.domian.entity;

import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_payment_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentHistory {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // 논리적 FK — payment 삭제돼도 히스토리는 보존
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "prev_status")
    private PaymentStatus prevStatus;   // 최초 생성 시 NULL

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false)
    private PaymentStatus nextStatus;

    @Column(name = "reason")
    private String reason;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 510)
    private String failureMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    // ================================
    // 팩토리 메서드
    // ================================

    public static PaymentHistory of(
            UUID paymentId,
            UUID orderId,
            PaymentType paymentType,
            PaymentStatus prevStatus,
            PaymentStatus nextStatus,
            String reason,
            int amount,
            String failureCode,
            String failureMessage
    ) {
        PaymentHistory history = new PaymentHistory();
        history.paymentId = paymentId;
        history.orderId = orderId;
        history.paymentType = paymentType;
        history.prevStatus = prevStatus;
        history.nextStatus = nextStatus;
        history.reason = reason;
        history.amount = amount;
        history.failureCode = failureCode;
        history.failureMessage = failureMessage;
        return history;
    }
}