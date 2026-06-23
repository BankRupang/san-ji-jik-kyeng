package com.bankrupang.sanjijk.notification.application.event;

import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import lombok.Getter;

@Getter
public class SlackNotificationEvent {

    private final NotificationLog notificationLog;

    public SlackNotificationEvent(NotificationLog notificationLog) {
        this.notificationLog = notificationLog;
    }
}
