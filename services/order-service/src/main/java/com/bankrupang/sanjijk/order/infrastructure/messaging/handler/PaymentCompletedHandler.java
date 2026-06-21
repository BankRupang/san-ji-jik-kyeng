package com.bankrupang.sanjijk.order.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.order.application.service.OrderService;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedHandler {

    private final OrderService orderService;

    public void handle(PaymentCompletedEvent event) {
        log.info("[HANDLER] PAYMENT_COMPLETED 처리 시작 - orderId: {}, winnerId: {}, paymentType: {}, occurredAt: {}",
                event.orderId(), event.winnerId(), event.paymentType(), event.occurredAt());

        orderService.completePayment(event);

        log.info("[HANDLER] PAYMENT_COMPLETED 처리 완료 - orderId: {}", event.orderId());
    }
}
