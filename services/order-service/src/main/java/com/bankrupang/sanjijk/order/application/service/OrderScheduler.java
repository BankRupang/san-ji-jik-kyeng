package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderRepository orderRepository;

    private final OrderSchedulerTransaction orderSchedulerTransaction;  // self 주입 제거

    @Scheduled(fixedDelay = 60000)
    public void expireUnpaidWinningOrders() {
        List<Order> unpaidOrders = orderRepository.findAllByOrderTypeAndStatusAndPaymentDueAtBefore(
                OrderType.WINNING, OrderStatus.PENDING, LocalDateTime.now());

        for (Order order : unpaidOrders) {
            orderSchedulerTransaction.expireUnpaidOne(order);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void expirePenaltyOrders() {
        List<Order> penaltyOrders = orderRepository.findAllByOrderTypeAndStatusAndPenaltyDueAtBefore(
                OrderType.WINNING, OrderStatus.PENALTY_PENDING, LocalDateTime.now());

        for (Order order : penaltyOrders) {
            orderSchedulerTransaction.expirePenaltyOne(order);
        }
    }

}
