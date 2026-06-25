package com.bankrupang.sanjijk.notification.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.notification.application.service.NotificationEventService;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.PaymentFailedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFailedHandler implements NotificationEventHandler {

    private final ObjectMapper objectMapper;
    private final NotificationEventService notificationEventService;

    @Override
    public void handle(JsonNode node) throws JsonProcessingException {
        notificationEventService.handlePaymentFailed(
                objectMapper.treeToValue(node, PaymentFailedEvent.class));
    }

    @Override
    public String getEventType() {
        return "PAYMENT_FAILED";
    }
}
