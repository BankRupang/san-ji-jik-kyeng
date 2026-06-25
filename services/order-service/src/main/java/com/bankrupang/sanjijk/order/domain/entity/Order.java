package com.bankrupang.sanjijk.order.domain.entity;

import com.bankrupang.sanjijk.common.entity.BaseEntity;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.exception.InvalidOrderStatusException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_orders", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "auction_id", "order_type"})
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "seller_id", updatable = false)
    private UUID sellerId;

    @Column(name = "user_name", nullable = false, length = 10)
    private String userName;

    @Column(name = "slack_id", nullable = false, length = 30)
    private String slackId;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "auction_title", nullable = false, length = 30)
    private String auctionTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false)
    private OrderType orderType;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "request_memo", length = 200)
    private String requestMemo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "payment_due_at")
    private LocalDateTime paymentDueAt;

    @Column(name = "penalty_due_at")
    private LocalDateTime penaltyDueAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Order(
            String orderNumber,
            UUID userId,
            UUID sellerId,
            String userName,
            String slackId,
            UUID auctionId,
            String auctionTitle,
            OrderType orderType,
            int amount,
            String requestMemo,
            LocalDateTime paymentDueAt
    ) {
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.sellerId = sellerId;
        this.userName = userName;
        this.slackId = slackId;
        this.auctionId = auctionId;
        this.auctionTitle = auctionTitle;
        this.orderType = orderType;
        this.amount = amount;
        this.requestMemo = requestMemo;
        this.status = OrderStatus.PENDING;
        this.paymentDueAt = paymentDueAt;
    }

    // 예치금 주문
    public static Order createDepositOrder(
            UUID userId,
            String userName,
            String slackId,
            UUID auctionId,
            String auctionTitle,
            int amount
    ) {
        return Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(userId)
                .userName(userName)
                .slackId(slackId)
                .auctionId(auctionId)
                .auctionTitle(auctionTitle)
                .orderType(OrderType.DEPOSIT)
                .amount(amount)
                .build();
    }

    // 낙찰 주문
    public static Order createWinningOrder(
            UUID userId,
            UUID sellerId,
            String userName,
            String slackId,
            UUID auctionId,
            String auctionTitle,
            int amount,
            String requestMemo
    ) {
        return Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(userId)
                .sellerId(sellerId)
                .userName(userName)
                .slackId(slackId)
                .auctionId(auctionId)
                .auctionTitle(auctionTitle)
                .orderType(OrderType.WINNING)
                .amount(amount)
                .requestMemo(requestMemo)
                .paymentDueAt(LocalDateTime.now().plusMinutes(15))
                .build();
    }

    // 토스에 전달할 주문 번호 -> 결제 서비스로
    private static String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ──────────────────────────────────────────
    // 상태 전이 메서드
    // ──────────────────────────────────────────

    // DEPOSIT: PENDING → PAYMENT_SUCCESS
// WINNING: PENDING → PAYMENT_SUCCESS
    public void markPaymentSuccess() {
        validateStatus(OrderStatus.PENDING);
        this.status = OrderStatus.PAYMENT_SUCCESS;
    }

    // WINNING: PAYMENT_SUCCESS → COMPLETED
    public void markCompleted() {
        validateStatus(OrderStatus.PAYMENT_SUCCESS);
        this.status = OrderStatus.COMPLETED;
    }

    // DEPOSIT: PAYMENT_SUCCESS → REFUNDED (경매 유찰 시)
    public void markRefunded() {
        validateStatus(OrderStatus.PAYMENT_SUCCESS);
        this.status = OrderStatus.REFUNDED;
    }

    // DEPOSIT: PAYMENT_SUCCESS → FORFEITED (낙찰자 결제 실패 시)
    public void markForfeited() {
        validateStatus(OrderStatus.PAYMENT_SUCCESS);
        this.status = OrderStatus.FORFEITED;
    }

    // WINNING: PENDING → PAYMENT_FAILED
    public void markPaymentFailed() {
        validateStatus(OrderStatus.PENDING);
        this.status = OrderStatus.PAYMENT_FAILED;
        this.penaltyDueAt = LocalDateTime.now().plusMinutes(15);
    }

    // WINNING: PAYMENT_FAILED → PENALTY_PENDING
    public void markPenaltyPending() {
        validateStatus(OrderStatus.PAYMENT_FAILED);
        this.status = OrderStatus.PENALTY_PENDING;
    }

    // WINNING: PENALTY_PENDING → COMPLETED (재결제 성공)
    public void markPenaltyCompleted() {
        validateStatus(OrderStatus.PENALTY_PENDING);
        this.status = OrderStatus.COMPLETED;
    }

    // WINNING: PENALTY_PENDING → EXPIRED (15분 초과)
    public void markExpired() {
        validateStatus(OrderStatus.PENALTY_PENDING);
        this.status = OrderStatus.EXPIRED;
    }

    // ──────────────────────────────────────────
    // 내부 유효성 검사
    // ──────────────────────────────────────────
    private void validateStatus(OrderStatus required) {
        if (this.status != required) {
            throw new InvalidOrderStatusException();
        }
    }

    @Version
    private Long version;

}
