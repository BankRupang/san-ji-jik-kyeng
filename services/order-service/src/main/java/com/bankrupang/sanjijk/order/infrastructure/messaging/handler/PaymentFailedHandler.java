package com.bankrupang.sanjijk.order.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.order.application.service.OrderService;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedHandler {

    private final OrderService orderService;

    public void handle(PaymentFailedEvent event) {
        log.warn("[HANDLER] PAYMENT_FAILED 처리 시작 - orderId: {}, winnerId: {}",
                event.orderId(), event.winnerId());

        orderService.failPayment(event);

        log.warn("[HANDLER] PAYMENT_FAILED 처리 완료 - orderId: {}", event.orderId());
    }
}
