package com.bankrupang.sanjijk.order.application.port;

import com.bankrupang.sanjijk.order.domain.entity.Order;

import java.time.LocalDateTime;

public interface OrderEventPublisher {

    void publishDepositCreated(Order order, LocalDateTime endAt);
    void publishWinningCreated(Order order, int depositAmount);
    void publishDepositForfeited(Order winningOrder, Order depositOrder);
}
