package com.bankrupang.sanjijk.order.presentation.dto.response;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String orderNumber,
        OrderType orderType,
        UUID auctionId,
        int amount,
        OrderStatus status,
        LocalDateTime paymentDueAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderType(),
                order.getAuctionId(),
                order.getAmount(),
                order.getStatus(),
                order.getPaymentDueAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
