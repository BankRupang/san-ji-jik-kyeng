package com.bankrupang.sanjijk.order.infrastructure.feign;

import com.bankrupang.sanjijk.order.infrastructure.feign.dto.UserInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/internal/users/{userId}")
    UserInfoResponse getUserInfo(@PathVariable UUID userId);
}

