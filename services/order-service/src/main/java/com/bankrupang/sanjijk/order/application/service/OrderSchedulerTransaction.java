package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import com.bankrupang.sanjijk.order.infrastructure.outbox.OrderOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSchedulerTransaction {

    private final OrderRepository orderRepository;
    private final OrderOutboxJpaRepository orderOutboxJpaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void expireUnpaidOne(Order order) {
        order.markPaymentFailed();
        order.markPenaltyPending();
        orderRepository.save(order);
        log.warn("[SCHEDULER] 미결제 낙찰 주문 PENALTY_PENDING 전환 - orderId: {}, userId: {}, orderType: {}, {} → PENALTY_PENDING, paymentDueAt: {}",
                order.getId(), order.getUserId(), order.getOrderType(), OrderStatus.PENDING, order.getPaymentDueAt());

    }

    @Transactional
    public void expirePenaltyOne(Order order) {
        order.markExpired();
        orderRepository.save(order);
        log.warn("[SCHEDULER] 패널티 만료 EXPIRED 전환 - orderId: {}, userId: {}, orderType: {}, PENALTY_PENDING → EXPIRED",
                order.getId(), order.getUserId(), order.getOrderType());

        orderRepository.findByUserIdAndAuctionIdAndOrderType(
                        order.getUserId(), order.getAuctionId(), OrderType.DEPOSIT)
                .ifPresent(depositOrder -> {
                    if (depositOrder.getStatus() != OrderStatus.PAYMENT_SUCCESS) {
                        log.warn("[SCHEDULER] 예치금 몰수 스킵 - 상태 불일치 orderId: {}, status: {}",
                                depositOrder.getId(), depositOrder.getStatus());
                        return;
                    }
                    depositOrder.markForfeited();
                    log.warn("[SCHEDULER] 예치금 몰수 처리 - orderId: {}, userId: {}, orderType: {}, PAYMENT_SUCCESS → FORFEITED",
                            depositOrder.getId(), depositOrder.getUserId(), depositOrder.getOrderType());
                });

        saveOutbox(order, "DEPOSIT_FORFEITED");
    }

    private void saveOutbox(Order order, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "orderId", order.getId().toString(),
                    "userId", order.getUserId().toString(),
                    "auctionId", order.getAuctionId().toString()
            ));
            OrderOutbox outbox = OrderOutbox.builder()
                    .aggregateType("Order")
                    .aggregateId(order.getId())
                    .eventType(eventType)
                    .payload(payload)
                    .build();
            orderOutboxJpaRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("[SCHEDULER] Outbox 저장 실패 - orderId: {}, eventType: {}", order.getId(), eventType, e);
        }
    }
}
