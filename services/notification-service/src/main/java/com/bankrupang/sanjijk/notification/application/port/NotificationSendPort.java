package com.bankrupang.sanjijk.notification.application.port;

public interface NotificationSendPort {
    void send(String slackId, String message);
}
