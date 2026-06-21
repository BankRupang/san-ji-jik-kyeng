package com.bankrupang.sanjijk.order.application.port;

import com.bankrupang.sanjijk.order.domain.entity.Order;

public interface OrderEventPublisher {

    void publishDepositCreated(Order order);
    void publishWinningCreated(Order order, int depositAmount);
}
