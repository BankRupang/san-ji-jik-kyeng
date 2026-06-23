package com.bankrupang.sanjijk.notification.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.notification.application.service.NotificationEventService;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.RefundCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundCompletedHandler implements NotificationEventHandler {

    private final ObjectMapper objectMapper;
    private final NotificationEventService notificationEventService;

    @Override
    public void handle(JsonNode node) throws JsonProcessingException {
        notificationEventService.handleRefundCompleted(
                objectMapper.treeToValue(node, RefundCompletedEvent.class));
    }

    @Override
    public String getEventType() {
        return "REFUND_COMPLETED";
    }
}
