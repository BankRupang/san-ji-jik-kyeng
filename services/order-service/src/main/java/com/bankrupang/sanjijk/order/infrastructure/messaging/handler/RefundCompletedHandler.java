package com.bankrupang.sanjijk.order.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.order.application.service.OrderService;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.RefundCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundCompletedHandler {

    private final OrderService orderService;

    public void handle(RefundCompletedEvent event) {
        log.info("[HANDLER] REFUND_COMPLETED 처리 시작 - orderId: {}, userId: {}, cancelAmount: {}, occurredAt: {}",
                event.orderId(), event.userId(), event.cancelAmount(), event.occurredAt());

        orderService.completeRefund(event);

        log.info("[HANDLER] REFUND_COMPLETED 처리 완료 - orderId: {}", event.orderId());
    }
}
