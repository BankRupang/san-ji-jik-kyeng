package com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer;

import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.WinningCreatedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.handler.WinningCreatedHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WinningCreatedConsumer {

    private final WinningCreatedHandler winningCreatedHandler;

    @KafkaListener(topics = "winning-created", groupId = "payment-service")
    public void consume(WinningCreatedEvent event) {
        log.info("[CONSUMER] WINNING_CREATED 수신 - orderId: {}, userId: {}, auctionId: {}",
                event.orderId(), event.userId(), event.auctionId());
        winningCreatedHandler.handle(event);
    }
}
