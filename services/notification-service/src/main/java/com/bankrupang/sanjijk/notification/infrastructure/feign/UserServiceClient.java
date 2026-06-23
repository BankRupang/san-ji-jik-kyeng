package com.bankrupang.sanjijk.notification.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "user-service",
        fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    @GetMapping("/internal/v1/users/{userId}/notification-enabled")
    UserNotificationResponse getNotificationEnabled(@PathVariable UUID userId);
}