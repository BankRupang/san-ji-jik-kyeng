package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.application.port.OrderEventPublisher;
import com.bankrupang.sanjijk.order.domain.entity.Order;
import org.springframework.stereotype.Service;

@Service
public class OrderEventService implements OrderEventPublisher {

    public void publishDepositCreated(Order order) {
        // TODO: Kafka 구현 시 작성
    }
}
