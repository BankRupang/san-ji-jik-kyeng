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

    @KafkaListener(topics = "bid-events.DLT", containerFactory = "notificationDltContainerFactory")
    public void consumeBidDlt(String payload) {
        log.error("DLT 수신 (bid-events) - 재시도 소진, 운영자 확인 필요: {}", payload);
    }

    @KafkaListener(topics = "payment-events.DLT", containerFactory = "notificationDltContainerFactory")
    public void consumePaymentDlt(String payload) {
        log.error("DLT 수신 (payment-events) - 재시도 소진, 운영자 확인 필요: {}", payload);
    }

    @KafkaListener(topics = "auction-events", containerFactory = "notificationContainerFactory")
    public void consumeAuctionEvents(String payload) {
        dispatch(payload);
    }

    @KafkaListener(topics = "bid-events", containerFactory = "notificationContainerFactory")
    public void consumeBidEvents(String payload) {
        dispatch(payload);
    }

    @KafkaListener(topics = "payment-events", containerFactory = "notificationContainerFactory")
    public void consumePaymentEvents(String payload) {
        dispatch(payload);
    }

    private void dispatch(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");

            if (type.isEmpty()) {
                log.warn("type 필드 없는 이벤트 스킵");
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
}
