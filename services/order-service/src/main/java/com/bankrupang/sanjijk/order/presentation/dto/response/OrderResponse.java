package com.bankrupang.sanjijk.order.presentation.dto.response;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userName,
        UUID auctionId,
        String auctionTitle,
        OrderType orderType,
        Long amount,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getUserName(),
                order.getAuctionId(),
                order.getAuctionTitle(),
                order.getOrderType(),
                order.getAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
