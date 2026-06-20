package com.bankrupang.sanjijk.order.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.order.application.service.OrderService;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.DepositForfeitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositForfeitedHandler {

    private final OrderService orderService;

    public void handle(DepositForfeitedEvent event) {
        log.warn("[HANDLER] DEPOSIT_FORFEITED 처리 시작 - orderId: {}, winnerId: {}",
                event.orderId(), event.winnerId());

        orderService.forfeitDeposit(event);

        log.warn("[HANDLER] DEPOSIT_FORFEITED 처리 완료 - orderId: {}", event.orderId());
    }
}
