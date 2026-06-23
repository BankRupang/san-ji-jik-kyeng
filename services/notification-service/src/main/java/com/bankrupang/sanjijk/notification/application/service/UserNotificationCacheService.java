package com.bankrupang.sanjijk.notification.application.service;

import com.bankrupang.sanjijk.notification.infrastructure.feign.UserNotificationResponse;
import com.bankrupang.sanjijk.notification.infrastructure.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserNotificationCacheService {

    private final UserServiceClient userServiceClient;

    @Cacheable(value = "notificationEnabled", key = "#userId.toString()")
    public UserNotificationResponse getNotificationEnabled(UUID userId) {
        return userServiceClient.getNotificationEnabled(userId).getData();
    }
}
