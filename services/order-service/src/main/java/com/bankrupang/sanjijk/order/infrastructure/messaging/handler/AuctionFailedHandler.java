package com.bankrupang.sanjijk.order.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.order.application.service.OrderService;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionFailedHandler {

    private final OrderService orderService;

    public void handle(AuctionFailedEvent event) {
        log.info("[HANDLER] AUCTION_FAILED 처리 시작 - auctionId: {}", event.auctionId());

        orderService.expireDepositOrders(event);

        log.info("[HANDLER] AUCTION_FAILED 처리 완료 - auctionId: {}", event.auctionId());
    }
}