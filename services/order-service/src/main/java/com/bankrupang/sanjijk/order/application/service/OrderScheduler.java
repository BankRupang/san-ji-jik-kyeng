package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
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
            try {
                orderSchedulerTransaction.expireUnpaidOne(order);
            } catch (Exception e) {
                log.error("[SCHEDULER] 미결제 주문 처리 실패 - orderId: {}", order.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void expirePenaltyOrders() {
        List<Order> penaltyOrders = orderRepository.findAllByOrderTypeAndStatusAndPenaltyDueAtBefore(
                OrderType.WINNING, OrderStatus.PENALTY_PENDING, LocalDateTime.now());

        for (Order order : penaltyOrders) {
            try {
                orderSchedulerTransaction.expirePenaltyOne(order);
            } catch (Exception e) {
                log.error("[SCHEDULER] 패널티 만료 주문 처리 실패 - orderId: {}", order.getId(), e);
            }
        }
    }

}
