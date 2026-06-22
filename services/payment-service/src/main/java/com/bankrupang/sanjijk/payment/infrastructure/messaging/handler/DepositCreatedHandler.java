package com.bankrupang.sanjijk.payment.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.payment.application.service.PaymentService;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.DepositCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositCreatedHandler {

    private final PaymentService paymentService;

    public void handle(DepositCreatedEvent event) {
        log.info("[HANDLER] DEPOSIT_CREATED 처리 시작 - orderId: {}", event.orderId());
        paymentService.createDepositPayment(event);
    }
}
