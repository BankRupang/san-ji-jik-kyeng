package com.bankrupang.sanjijk.order.domain.repository;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUserIdAndAuctionIdAndOrderType(UUID userId, UUID auctionId, OrderType orderType);

    List<Order> findAllByAuctionIdAndOrderType(UUID auctionId, OrderType orderType);

    List<Order> findAllByOrderTypeAndStatusAndPaymentDueAtBefore(OrderType orderType, OrderStatus status, LocalDateTime now);
    List<Order> findAllByOrderTypeAndStatusAndPenaltyDueAtBefore(OrderType orderType, OrderStatus status, LocalDateTime now);
}
