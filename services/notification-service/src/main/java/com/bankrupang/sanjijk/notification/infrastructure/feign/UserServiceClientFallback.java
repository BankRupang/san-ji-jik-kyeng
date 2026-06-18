package com.bankrupang.sanjijk.notification.infrastructure.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Value("${notification.feign.fallback.slack-id:}")
    private String fallbackSlackId;

    @Value("${notification.feign.fallback.notification-allow:false}")
    private boolean fallbackNotificationAllow;

    @Override
    public UserNotificationResponse getNotificationEnabled(UUID userId) {
        log.warn("user-service 호출 실패, fallback 사용 userId={}", userId);
        return new UserNotificationResponse(userId, fallbackSlackId, fallbackNotificationAllow);
    }
}