package com.bankrupang.sanjijk.order.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.order.application.service.OrderService;
import com.bankrupang.sanjijk.order.infrastructure.feign.UserClient;
import com.bankrupang.sanjijk.order.infrastructure.feign.dto.UserInfoResponse;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionWonHandler {

    private final OrderService orderService;
    private final UserClient userClient;

    public void handle(AuctionWonEvent event) {
        log.info("[HANDLER] AUCTION_WON 처리 시작 - auctionId: {}, winnerId: {}, finalPrice: {}, occurredAt: {}",
                event.auctionId(), event.winnerId(), event.finalPrice(), event.occurredAt());

        // 멱등성 체크
        if (orderService.existsWinningOrder(event.auctionId(), event.winnerId())) {
            log.warn("[HANDLER] AUCTION_WON 중복 수신 - auctionId: {}, winnerId: {}",
                    event.auctionId(), event.winnerId());
            return;
        }

        // user-service에서 name, slackId 조회
        UserInfoResponse userInfo = userClient.getUserInfo(event.winnerId()).getData();

        // 낙찰 주문 생성
        orderService.createWinningOrder(event, userInfo);

        log.info("[HANDLER] AUCTION_WON 처리 완료 - auctionId: {}, winnerId: {}",
                event.auctionId(), event.winnerId());
    }
}
