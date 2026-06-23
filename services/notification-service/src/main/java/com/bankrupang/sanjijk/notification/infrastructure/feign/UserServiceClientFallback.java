package com.bankrupang.sanjijk.notification.infrastructure.feign;

import com.bankrupang.sanjijk.common.response.ApiResponse;
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
    public ApiResponse<UserNotificationResponse> getNotificationEnabled(UUID userId) {
        log.warn("user-service 호출 실패, fallback 사용 userId={}", userId);
        return ApiResponse.ok(new UserNotificationResponse(userId, fallbackSlackId, fallbackNotificationAllow));
    }
}