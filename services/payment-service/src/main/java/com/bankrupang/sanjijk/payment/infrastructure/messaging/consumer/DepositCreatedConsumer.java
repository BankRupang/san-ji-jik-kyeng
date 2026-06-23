package com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer;

import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.DepositCreatedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.handler.DepositCreatedHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositCreatedConsumer {

    private final DepositCreatedHandler depositCreatedHandler;

    @KafkaListener(topics = "deposit-created", groupId = "payment-service")
    public void consume(DepositCreatedEvent event) {
        log.info("[CONSUMER] DEPOSIT_CREATED 수신 - orderId: {}, userId: {}, auctionId: {}",
                event.orderId(), event.userId(), event.auctionId());
        depositCreatedHandler.handle(event);
    }
}
