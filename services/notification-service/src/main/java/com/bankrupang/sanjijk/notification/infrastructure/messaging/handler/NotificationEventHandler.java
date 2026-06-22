package com.bankrupang.sanjijk.notification.infrastructure.messaging.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public interface NotificationEventHandler {
    void handle(JsonNode node) throws JsonProcessingException;
    String getEventType();
}