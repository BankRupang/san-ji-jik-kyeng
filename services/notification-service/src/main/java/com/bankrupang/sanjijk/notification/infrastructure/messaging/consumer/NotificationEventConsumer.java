package com.bankrupang.sanjijk.notification.infrastructure.messaging.consumer;

import com.bankrupang.sanjijk.notification.infrastructure.messaging.handler.NotificationEventHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final List<NotificationEventHandler> handlerList;
    private Map<String, NotificationEventHandler> handlers;

    @PostConstruct
    public void init() {
        handlers = handlerList.stream()
                .collect(Collectors.toMap(NotificationEventHandler::getEventType, h -> h));
    }

    @KafkaListener(topics = "auction-events.DLT", containerFactory = "notificationDltContainerFactory")
    public void consumeAuctionDlt(String payload) {
        log.error("DLT 수신 (auction-events) - 재시도 소진, 운영자 확인 필요: {}", payload);
    }

    @KafkaListener(topics = "bid-overtaken.DLT", containerFactory = "notificationDltContainerFactory")
    public void consumeBidOvertakenDlt(String payload) {
        log.error("DLT 수신 (bid-overtaken) - 재시도 소진, 운영자 확인 필요: {}", payload);
    }

    @KafkaListener(topics = "payment-completed.DLT", containerFactory = "notificationDltContainerFactory")
    public void consumePaymentCompletedDlt(String payload) {
        log.error("DLT 수신 (payment-completed) - 재시도 소진, 운영자 확인 필요: {}", payload);
    }

    @KafkaListener(topics = "payment-failed.DLT", containerFactory = "notificationDltContainerFactory")
    public void consumePaymentFailedDlt(String payload) {
        log.error("DLT 수신 (payment-failed) - 재시도 소진, 운영자 확인 필요: {}", payload);
    }

    @KafkaListener(topics = "refund-completed.DLT", containerFactory = "notificationDltContainerFactory")
    public void consumeRefundCompletedDlt(String payload) {
        log.error("DLT 수신 (refund-completed) - 재시도 소진, 운영자 확인 필요: {}", payload);
    }

    @KafkaListener(topics = "deposit-forfeited.DLT", containerFactory = "notificationDltContainerFactory")
    public void consumeDepositForfeitedDlt(String payload) {
        log.error("DLT 수신 (deposit-forfeited) - 재시도 소진, 운영자 확인 필요: {}", payload);
    }

    @KafkaListener(topics = "auction-events", containerFactory = "notificationContainerFactory")
    public void consumeAuctionEvents(String payload) {
        dispatch(payload);
    }

    @KafkaListener(topics = "bid-overtaken", containerFactory = "notificationContainerFactory")
    public void consumeBidOvertaken(String payload) {
        dispatchDirect(payload, "BID_OVERTAKEN");
    }

    @KafkaListener(topics = "payment-completed", containerFactory = "notificationContainerFactory")
    public void consumePaymentCompleted(String payload) {
        dispatchDirect(payload, "PAYMENT_COMPLETED");
    }

    @KafkaListener(topics = "payment-failed", containerFactory = "notificationContainerFactory")
    public void consumePaymentFailed(String payload) {
        dispatchDirect(payload, "PAYMENT_FAILED");
    }

    @KafkaListener(topics = "refund-completed", containerFactory = "notificationContainerFactory")
    public void consumeRefundCompleted(String payload) {
        dispatchDirect(payload, "REFUND_COMPLETED");
    }

    @KafkaListener(topics = "deposit-forfeited", containerFactory = "notificationContainerFactory")
    public void consumeDepositForfeited(String payload) {
        dispatchDirect(payload, "DEPOSIT_FORFEITED");
    }

    private void dispatch(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("eventType").asText("");

            if (type.isEmpty()) {
                log.warn("eventType 필드 없는 이벤트 스킵");
                return;
            }

            NotificationEventHandler handler = handlers.get(type);
            if (handler == null) {
                log.warn("알 수 없는 이벤트 타입: {}", type);
                return;
            }

            handler.handle(node);

        } catch (JsonProcessingException e) {
            // 역직렬화 불가 메시지는 재시도해도 동일하게 실패하므로 의도적으로 스킵 (poison pill 방지)
            log.error("이벤트 파싱 실패 (스킵): {}", e.getMessage());
        } catch (Exception e) {
            log.error("이벤트 처리 실패: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void dispatchDirect(String payload, String eventType) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            NotificationEventHandler handler = handlers.get(eventType);
            if (handler == null) {
                log.warn("핸들러 없음: {}", eventType);
                return;
            }
            handler.handle(node);
        } catch (JsonProcessingException e) {
            log.error("이벤트 파싱 실패 (스킵): {}", e.getMessage());
        } catch (Exception e) {
            log.error("이벤트 처리 실패: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
