package com.bankrupang.sanjijk.order.domain.repository;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUserIdAndAuctionIdAndOrderType(UUID userId, UUID auctionId, OrderType orderType);

    Page<Order> findAllByUserIdAndOrderType(UUID userId, OrderType orderType, Pageable pageable);

    List<Order> findAllByOrderTypeAndStatusAndPaymentDueAtBefore(OrderType orderType, OrderStatus status, LocalDateTime now);
    List<Order> findAllByOrderTypeAndStatusAndPenaltyDueAtBefore(OrderType orderType, OrderStatus status, LocalDateTime now);
}
