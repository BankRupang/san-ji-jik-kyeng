package com.bankrupang.sanjijk.payment.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.payment.application.service.PaymentService;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionFailedHandler {

    private final PaymentService paymentService;

    public void handle(AuctionFailedEvent event) {
        log.info("[HANDLER] AUCTION_FAILED 처리 시작 - auctionId: {}", event.auctionId());
        paymentService.refundAllDeposits(event);
    }
}
