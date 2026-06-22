package com.bankrupang.sanjijk.order.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.order.infrastructure.outbox.OrderOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionFailedHandler {

    private final OrderOutboxJpaRepository orderOutboxJpaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(AuctionFailedEvent event) {
        log.info("[AUCTION_FAILED] 유찰 환불 요청 - auctionId: {}", event.auctionId());

        // 환불 대상자 산출은 payment-service가 자체 결제 기록으로 처리
        // order-service는 auctionId만 전달
        saveOutbox(event.auctionId());
    }

    private void saveOutbox(UUID auctionId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "auctionId", auctionId.toString()
            ));
            OrderOutbox outbox = OrderOutbox.builder()
                    .aggregateType("Order")
                    .aggregateId(auctionId)
                    .eventType("REFUND_REQUESTED")
                    .payload(payload)
                    .build();
            orderOutboxJpaRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("[AUCTION_FAILED] Outbox 저장 실패 - auctionId: {}", auctionId, e);
        }
    }
}
