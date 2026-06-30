package com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bankrupang.sanjijk.auction.auction.application.service.AuctionService;
import com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto.AuctionEndedEvent;
import com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto.DepositForfeitedEvent;
import com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto.PaymentCompletedEvent;
import com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto.PaymentFailedEvent;
import com.bankrupang.sanjijk.auction.global.util.AuctionLogContext;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExternalEventConsumer {

    private static final String AUCTION_SERVICE_GROUP_ID = "auction-service";

    private final ObjectMapper objectMapper;
    private final AuctionService auctionService;

    @KafkaListener(topics = "auction-ended", groupId = AUCTION_SERVICE_GROUP_ID)
    public void consumeAuctionEnded(String payload) {
        AuctionEndedEvent event = readEvent(payload, AuctionEndedEvent.class, "AUCTION_ENDED");

        AuctionLogContext.runWithAuctionId(event.auctionId(), () -> {
            log.info("[KAFKA][CONSUME] AUCTION_ENDED 수신 - auctionId: {}, hasBid: {}, winnerId: {}, finalPrice: {}, endedAt: {}",
                    event.auctionId(), event.hasBid(), event.winnerId(), event.finalPrice(), event.endedAt());

            auctionService.closeAuctionByEndedEvent(
                    event.auctionId(),
                    event.hasBid(),
                    event.winnerId(),
                    event.finalPrice()
            );
        });
    }

    @KafkaListener(topics = "payment-completed", groupId = AUCTION_SERVICE_GROUP_ID)
    public void consumePaymentCompleted(String payload) {
        PaymentCompletedEvent event = readEvent(payload, PaymentCompletedEvent.class, "PAYMENT_COMPLETED");

        AuctionLogContext.runWithAuctionId(event.auctionId(), () -> {
            log.info("[KAFKA][CONSUME] PAYMENT_COMPLETED 수신 - orderId: {}, auctionId: {}, winnerId: {}, paidAmount: {}, occurredAt: {}",
                    event.orderId(), event.auctionId(), event.winnerId(), event.paidAmount(), event.occurredAt());

            auctionService.completeAuctionPayment(event.auctionId());
        });
    }

    @KafkaListener(topics = "payment-failed", groupId = AUCTION_SERVICE_GROUP_ID)
    public void consumePaymentFailed(String payload) {
        PaymentFailedEvent event = readEvent(payload, PaymentFailedEvent.class, "PAYMENT_FAILED");

        AuctionLogContext.runWithAuctionId(event.auctionId(), () -> {
            log.warn("[KAFKA][CONSUME] PAYMENT_FAILED 수신 - orderId: {}, auctionId: {}, winnerId: {}, failureMessage: {}, occurredAt: {}",
                    event.orderId(), event.auctionId(), event.winnerId(), event.failureMessage(), event.occurredAt());

            auctionService.failAuctionPayment(event.auctionId());
        });
    }

    @KafkaListener(topics = "deposit-forfeited", groupId = AUCTION_SERVICE_GROUP_ID)
    public void consumeDepositForfeited(String payload) {
        DepositForfeitedEvent event = readEvent(payload, DepositForfeitedEvent.class, "DEPOSIT_FORFEITED");

        AuctionLogContext.runWithAuctionId(event.auctionId(), () -> {
            log.warn("[KAFKA][CONSUME] DEPOSIT_FORFEITED 수신 - orderId: {}, auctionId: {}, winnerId: {}, forfeitedAmount: {}, occurredAt: {}",
                    event.orderId(), event.auctionId(), event.winnerId(), event.forfeitedAmount(), event.occurredAt());

            auctionService.failAuctionPayment(event.auctionId());
        });
    }

    private <T> T readEvent(String payload, Class<T> eventType, String eventName) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JsonProcessingException e) {
            log.warn("[KAFKA][CONSUME] {} payload 역직렬화 실패 - payload: {}", eventName, payload, e);
            throw new IllegalArgumentException(eventName + " payload 역직렬화에 실패했습니다.", e);
        }
    }
}
