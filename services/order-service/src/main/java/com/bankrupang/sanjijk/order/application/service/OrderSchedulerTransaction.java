package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSchedulerTransaction {

    private final OrderRepository orderRepository;

    @Transactional
    public void expireUnpaidOne(Order order) {
        order.markPaymentFailed();
        order.markPenaltyPending();
        orderRepository.save(order); // DB에 저장 안 되는 문제 해결
        log.warn("[SCHEDULER] 미결제 낙찰 주문 PENALTY_PENDING 전환 - orderId: {}, userId: {}, orderType: {}, {} → PENALTY_PENDING, paymentDueAt: {}",
                order.getId(), order.getUserId(), order.getOrderType(), OrderStatus.PENDING, order.getPaymentDueAt());
    }

    @Transactional
    public void expirePenaltyOne(Order order) {
        order.markExpired();
        orderRepository.save(order); // depositOrder는 같은 트랜잭션에서 조회됐으니 save() 불필요
        log.warn("[SCHEDULER] 패널티 만료 EXPIRED 전환 - orderId: {}, userId: {}, orderType: {}, PENALTY_PENDING → EXPIRED",
                order.getId(), order.getUserId(), order.getOrderType());

        orderRepository.findByUserIdAndAuctionIdAndOrderType(
                        order.getUserId(), order.getAuctionId(), OrderType.DEPOSIT)
                .ifPresent(depositOrder -> {
                    depositOrder.markForfeited();
                    log.warn("[SCHEDULER] 예치금 몰수 처리 - orderId: {}, userId: {}, orderType: {}, PAYMENT_SUCCESS → FORFEITED",
                            depositOrder.getId(), depositOrder.getUserId(), depositOrder.getOrderType());
                });
    }
}

