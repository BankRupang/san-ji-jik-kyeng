package com.bankrupang.sanjijk.notification.infrastructure.messaging.handler;

import com.bankrupang.sanjijk.notification.application.service.NotificationEventService;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.BidOvertakenEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidOvertakenHandler implements NotificationEventHandler {

    private final ObjectMapper objectMapper;
    private final NotificationEventService notificationEventService;

    @Override
    public void handle(JsonNode node) throws JsonProcessingException {
        notificationEventService.handleBidOvertaken(
                objectMapper.treeToValue(node, BidOvertakenEvent.class));
    }

    @Override
    public String getEventType() {
        return "BID_OVERTAKEN";
    }
}