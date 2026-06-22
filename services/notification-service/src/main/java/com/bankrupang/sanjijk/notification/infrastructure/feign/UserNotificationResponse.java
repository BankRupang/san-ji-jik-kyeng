package com.bankrupang.sanjijk.notification.infrastructure.feign;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationResponse {
    private UUID userId;
    private String slackId;
    private boolean notificationAllow;
}
